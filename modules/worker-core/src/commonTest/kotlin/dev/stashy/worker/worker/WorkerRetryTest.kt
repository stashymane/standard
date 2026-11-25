package dev.stashy.worker.worker

import dev.stashy.worker.TestJson
import dev.stashy.worker.Worker
import dev.stashy.worker.create
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.QueuedJob
import dev.stashy.worker.job.RetryPolicy
import dev.stashy.worker.schedule.Schedule
import dev.stashy.worker.store.JobHistory
import dev.stashy.worker.store.JobQueue
import dev.stashy.worker.store.memory.InMemoryJobHistory
import dev.stashy.worker.store.memory.InMemoryJobQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.serializer
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerRetryTest {
    @Test
    fun `failed preparation disposes resources and retries`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val disposed = mutableListOf<String>()
        var attempts = 0
        var executions = 0
        val worker = worker(
            clock = clock,
            store = store,
            retry = RetryPolicy.Exponential(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0),
            poll = 1.seconds,
            prepareWindow = 30.seconds,
        ) {
            job<Unit> { _, _ ->
                val value by resource { "attempt" }.onDispose { disposed += it }
                check(value == "attempt")
                attempts++
                if (attempts == 1) error("preparation failed")
                handler { executions++ }
            }
        }

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()
        assertEquals(1, attempts)
        assertEquals(0, executions)
        assertEquals(listOf("attempt"), disposed)

        advanceTimeBy(10.seconds)
        runCurrent()
        assertEquals(2, attempts)
        assertEquals(1, executions)
        assertEquals(listOf("attempt", "attempt"), disposed)
        worker.stop()
    }

    @Test
    fun `retry waits for its retry instant when the prepare window is larger`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var attempts = 0
        val worker = worker(
            clock = clock,
            store = store,
            retry = RetryPolicy.Exponential(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0),
            poll = 1.seconds,
            prepareWindow = 30.seconds,
        ) {
            job<Unit> { _, _ ->
                handler {
                    attempts++
                    if (attempts == 1) error("boom")
                }
            }
        }

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()
        assertEquals(1, attempts)

        advanceTimeBy(8.seconds)
        runCurrent()
        assertEquals(1, attempts)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(2, attempts)
        worker.stop()
    }

    @Test
    fun `failed job is retried after its delay`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var attempts = 0
        val worker = worker(
            clock = clock,
            store = store,
            retry = RetryPolicy.Exponential(maxAttempts = 2, initialDelay = 10.seconds, jitter = 0.0),
        ) {
            job<Unit> { _, _ ->
                handler {
                    attempts++
                    if (attempts == 1) error("boom")
                }
            }
        }

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
        worker.start(backgroundScope)

        runCurrent()
        assertEquals(1, attempts)
        assertEquals(QueuedJob.Status.PENDING, assertNotNull(store.get(Job.Id("job"))).status)

        advanceTimeBy(9.seconds)
        runCurrent()
        assertEquals(1, attempts)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(2, attempts)
        assertNull(store.get(Job.Id("job")))

        worker.stop()
    }

    @Test
    fun `null max attempts retries without limit`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var attempts = 0
        val worker = worker(
            clock = clock,
            store = store,
            retry = RetryPolicy.Delayed(maxAttempts = null, delay = 1.seconds),

        ) {
            job<Unit> { _, _ ->
                handler {
                    attempts++
                    if (attempts <= 6) error("boom")
                }
            }
        }

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
        worker.start(backgroundScope)

        runCurrent()
        assertEquals(1, attempts)

        repeat(6) {
            advanceTimeBy(10.seconds)
            runCurrent()
        }
        assertEquals(7, attempts)
        assertNull(store.get(Job.Id("job")))

        worker.stop()
    }

    @Test
    fun `handler errors are retried like exceptions`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var attempts = 0
        val worker = worker(
            clock = clock,
            store = store,
            retry = RetryPolicy.Exponential(maxAttempts = 2, initialDelay = 10.seconds, jitter = 0.0),
        ) {
            job<Unit> { _, _ ->
                handler {
                    attempts++
                    if (attempts == 1) throw AssertionError("boom")
                }
            }
        }

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()
        assertEquals(1, attempts)
        assertEquals(QueuedJob.Status.PENDING, assertNotNull(store.get(Job.Id("job"))).status)

        advanceTimeBy(11.seconds)
        runCurrent()
        assertEquals(2, attempts)
        assertNull(store.get(Job.Id("job")))

        worker.stop()
    }

    @Test
    fun `expired lease is retried after the configured delay`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        val handlerId = serializer<Unit>().descriptor.serialName
        val retry = RetryPolicy.Exponential(maxAttempts = 3, initialDelay = 3.seconds, jitter = 0.0)
        val worker = worker(
            clock = clock,
            store = store,
            history = history,
            retry = retry,
            lease = 5.seconds,
            heartbeat = 1.seconds,
            poll = 1.seconds,
        ) {
            job<Unit> { _, _ -> handler { } }
        }

        val definition = Job(
            id = Job.Id("crashed"),
            handlerId = handlerId,
            schedule = Schedule.Once(clock.now()),
            retry = retry,
        )
        store.create(definition, Unit, serializer(), clock.now())
        store.claim(
            JobQueue.ClaimRequest(
                now = clock.now(),
                supportedHandlerIds = setOf(handlerId),
                prepareWindow = 1.seconds,
                lease = 5.seconds,
                workerId = "dead-worker",
            ),
        )

        worker.start(backgroundScope)
        advanceTimeBy(6.seconds)
        runCurrent()

        val job = assertNotNull(store.get(definition.id))
        assertEquals(QueuedJob.Status.PENDING, job.status)
        assertEquals(1, job.attempt)
        assertEquals(Instant.fromEpochMilliseconds(8_000), job.nextRun)
        val entry = assertNotNull(history.list().singleOrNull())
        assertEquals(JobHistory.Status.FAILED, entry.status)
        assertEquals("Lease expired after 1 attempt(s)", entry.message)

        worker.stop()
    }

    @Test
    fun `invalid payload fails the job without retrying`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            job<String> { _, _ -> handler { runs++ } }
        }

        store.create(
            Job(
                id = Job.Id("invalid"),
                handlerId = serializer<String>().descriptor.serialName,
                schedule = Schedule.Once(clock.now()),
                retry = RetryPolicy.Exponential(maxAttempts = 3, initialDelay = 1.seconds, jitter = 0.0),
            ),
            config = 123,
            serializer = serializer(),
            scheduledAt = clock.now(),
        )
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(0, runs)
        assertNull(store.get(Job.Id("invalid")))
        worker.stop()
    }

    @Test
    fun `enqueued jobs use their block's default retry policy`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val retry = RetryPolicy.Exponential(maxAttempts = 9, initialDelay = 1.seconds, jitter = 0.0)
        val worker = Worker.Companion(id = "worker") {
            jobs(store, config = { this.retry = retry }) {
                job<Unit> { _, _ -> handler { } }
            }
            config {
                this.clock = clock
                dispatcher = StandardTestDispatcher(testScheduler)
            }
        }

        val id = worker.enqueue(Unit, at = clock.now())
        assertEquals(retry, assertNotNull(store.get(id)).definition.retry)
    }
}
