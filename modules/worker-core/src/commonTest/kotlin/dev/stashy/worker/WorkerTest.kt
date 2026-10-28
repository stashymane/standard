package dev.stashy.worker

import dev.stashy.worker.job.InMemoryJobStore
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.JobStore
import dev.stashy.worker.job.LateExecutionPolicy
import dev.stashy.worker.job.RetryPolicy
import dev.stashy.worker.schedule.Schedule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerTest {
    @Test
    fun `worker executes a due job`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobStore(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            handler<Unit> { _, _ -> runs++ }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, runs)
        worker.stop()
    }

    @Test
    fun `worker does not start a job before its instant`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobStore(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            handler<Unit> { _, _ -> runs++ }
        }
        val at = Instant.fromEpochMilliseconds(60_000)

        worker.schedule("job", Unit) { once(at) }
        worker.start(backgroundScope)

        advanceTimeBy(50.seconds)
        runCurrent()
        assertEquals(0, runs)

        advanceTimeBy(9.seconds)
        runCurrent()
        assertEquals(0, runs)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(1, runs)

        worker.stop()
    }

    @Test
    fun `failed job is retried after its delay`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobStore(TestJson)
        var attempts = 0
        val worker = worker(
            clock = clock,
            store = store,
            retry = RetryPolicy(maxAttempts = 2, initialDelay = 10.seconds, jitter = 0.0),
        ) {
            handler<Unit> { _, _ ->
                attempts++
                if (attempts == 1) error("boom")
            }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)

        runCurrent()
        assertEquals(1, attempts)
        assertEquals(Job.Status.PENDING, assertNotNull(store.get(Job.Id("job"))).status)

        advanceTimeBy(9.seconds)
        runCurrent()
        assertEquals(1, attempts)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(2, attempts)
        assertEquals(Job.Status.COMPLETED, assertNotNull(store.get(Job.Id("job"))).status)

        worker.stop()
    }

    @Test
    fun `recurring job schedules its next occurrence`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobStore(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            handler<Unit> { _, _ -> runs++ }
        }

        worker.schedule("job", Unit) { every(20.seconds) }
        worker.start(backgroundScope)

        advanceTimeBy(21.seconds)
        runCurrent()
        assertEquals(1, runs)

        val job = assertNotNull(store.get(Job.Id("job")))
        assertEquals(Job.Status.PENDING, job.status)
        assertEquals(Instant.fromEpochMilliseconds(40_000), job.nextRun)

        advanceTimeBy(18.seconds)
        runCurrent()
        assertEquals(1, runs)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(2, runs)
        worker.stop()
    }

    @Test
    fun `expired lease is retried after the configured delay`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobStore(TestJson)
        val handlerId = serializer<Unit>().descriptor.serialName
        val retry = RetryPolicy(maxAttempts = 3, initialDelay = 3.seconds, jitter = 0.0)
        val worker = worker(
            clock = clock,
            store = store,
            retry = retry,
            lease = 5.seconds,
            heartbeat = 1.seconds,
            poll = 1.seconds,
        ) {
            handler<Unit> { _, _ -> }
        }

        val definition = Job.Definition(
            id = Job.Id("crashed"),
            handlerId = handlerId,
            schedule = Schedule.Once(clock.now()),
            retry = retry,
        )
        store.create(definition, Unit, serializer(), clock.now())
        store.claim(
            JobStore.ClaimRequest(
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
        assertEquals(Job.Status.PENDING, job.status)
        assertEquals(1, job.attempt)
        assertEquals(Instant.fromEpochMilliseconds(8_000), job.nextRun)

        worker.stop()
    }

    @Test
    fun `late one-shot job fails when its tolerance is exceeded`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobStore(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            handler<Unit> { _, _ -> runs++ }
        }

        worker.schedule("job", Unit) {
            once(Instant.fromEpochMilliseconds(0))
            latePolicy = LateExecutionPolicy.FailAfter(5.seconds)
        }

        advanceTimeBy(10.seconds)
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(0, runs)
        assertEquals(Job.Status.FAILED, assertNotNull(store.get(Job.Id("job"))).status)
        worker.stop()
    }

    @Test
    fun `invalid payload fails the job without retrying`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobStore(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            handler<String> { _, _ -> runs++ }
        }

        store.create(
            Job.Definition(
                id = Job.Id("invalid"),
                handlerId = serializer<String>().descriptor.serialName,
                schedule = Schedule.Once(clock.now()),
                retry = RetryPolicy(maxAttempts = 3, initialDelay = 1.seconds, jitter = 0.0),
            ),
            config = 123,
            serializer = serializer(),
            scheduledAt = clock.now(),
        )
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(0, runs)
        val job = assertNotNull(store.get(Job.Id("invalid")))
        assertEquals(Job.Status.FAILED, job.status)
        worker.stop()
    }

    @Test
    fun `shutdown releases an interrupted job for retry`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobStore(TestJson)
        val started = CompletableDeferred<Unit>()
        val worker = worker(clock, store) {
            handler<Unit> { _, _ ->
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()
        assertTrue(started.isCompleted)

        worker.stop()

        val job = assertNotNull(store.get(Job.Id("job")))
        assertEquals(Job.Status.PENDING, job.status)
        assertNotNull(job.nextRun)
        worker.stop()
    }

    @Test
    fun `sync preserves runtime state and resets a changed schedule`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobStore(TestJson)
        val worker = worker(clock, store) {
            handler<Unit> { _, _ -> }
        }

        val id = worker.schedule("job", Unit) { once(clock.now()) }
        val definition = assertNotNull(store.get(id)).definition
        val claim = assertNotNull(
            store.claim(
                JobStore.ClaimRequest(
                    now = clock.now(),
                    supportedHandlerIds = setOf(definition.handlerId),
                    prepareWindow = 1.seconds,
                    lease = 1.minutes,
                    workerId = "other",
                ),
            ),
        )
        store.reschedule(claim, clock.now(), clock.now() + 10.seconds, "retry")

        worker.sync(definition, Unit)
        val preserved = assertNotNull(store.get(id))
        assertEquals(Job.Status.PENDING, preserved.status)
        assertEquals(1, preserved.attempt)

        val changed = worker.schedule("job", Unit) {
            every(1.minutes, startAt = clock.now() + 5.minutes)
        }
        assertEquals(id, changed)
        val updated = assertNotNull(store.get(id))
        assertEquals(Job.Status.PENDING, updated.status)
        assertEquals(0, updated.attempt)
        assertEquals(clock.now() + 5.minutes, updated.nextRun)
    }

    @Test
    fun `handlers are selected by config serial name`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobStore(TestJson)
        var strings = 0
        var ints = 0
        val worker = worker(clock, store) {
            handler<String> { _, _ -> strings++ }
            handler<Int> { _, _ -> ints++ }
        }

        worker.enqueue("hello", at = clock.now())
        worker.enqueue(42, at = clock.now())
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, strings)
        assertEquals(1, ints)
        worker.stop()
    }

    @Test
    fun `duplicate handler configs are rejected`() {
        val store = InMemoryJobStore(TestJson)

        assertFailsWith<IllegalArgumentException> {
            Worker(id = "worker", store = store) {
                handlers {
                    handler<Unit> { _, _ -> }
                    handler<Unit> { _, _ -> }
                }
            }
        }
    }

    private fun TestScope.worker(
        clock: Clock,
        store: JobStore,
        retry: RetryPolicy = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0),
        lease: Duration = 1.minutes,
        heartbeat: Duration = 20.seconds,
        poll: Duration = 10.seconds,
        configureHandlers: HandlerRegistry.() -> Unit,
    ): Worker = Worker(id = "worker", store = store) {
        handlers(configureHandlers)
        config {
            pollInterval = poll
            prepareWindow = poll
            leaseDuration = lease
            heartbeatInterval = heartbeat
            maxConcurrentJobs = 4
            shutdownGracePeriod = 1.seconds
            this.retry = retry
            this.clock = clock
            dispatcher = StandardTestDispatcher(testScheduler)
        }
    }

    private class SchedulerClock(private val scheduler: TestCoroutineScheduler) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(scheduler.currentTime)
    }
}
