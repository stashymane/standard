package dev.stashy.worker

import dev.stashy.worker.handler.JobsRegistry
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.LateExecutionPolicy
import dev.stashy.worker.job.QueuedJob
import dev.stashy.worker.job.RetryPolicy
import dev.stashy.worker.schedule.Schedule
import dev.stashy.worker.store.JobHistory
import dev.stashy.worker.store.JobQueue
import dev.stashy.worker.store.memory.InMemoryJobHistory
import dev.stashy.worker.store.memory.InMemoryJobQueue
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
import kotlin.test.assertNull
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
        val store = InMemoryJobQueue(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> runs++ }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, runs)
        worker.stop()
    }

    @Test
    fun `finished attempts are recorded in history`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        var runs = 0
        val worker = worker(clock, store, history = history) {
            job<Unit> { _, _ -> runs++ }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, runs)
        val entry = assertNotNull(history.list().singleOrNull())
        assertEquals(Job.Id("job"), entry.jobId)
        assertEquals(1, entry.attempt)
        assertEquals(JobHistory.Status.SUCCEEDED, entry.status)
        assertNull(entry.message)
        worker.stop()
    }

    @Test
    fun `worker does not start a job before its instant`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> runs++ }
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
    fun `prepared handler prepares in advance and executes at its instant`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var preparations = 0
        var executions = 0
        val worker = worker(clock, store, prepareWindow = 1.minutes) {
            prepared<Unit> { _, _ ->
                preparations++
                handler { executions++ }
            }
        }
        val at = Instant.fromEpochMilliseconds(60_000)

        worker.schedule("job", Unit) { once(at) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, preparations)
        assertEquals(0, executions)

        advanceTimeBy(59.seconds)
        runCurrent()
        assertEquals(1, preparations)
        assertEquals(0, executions)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(1, executions)
        worker.stop()
    }

    @Test
    fun `prepared resources are disposed in reverse order`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val disposed = mutableListOf<String>()
        var executions = 0
        val worker = worker(clock, store) {
            prepared<Unit> { _, _ ->
                val first by resource { "first" }
                    .onDispose { disposed += "first:$it" }
                val second by resource { "second" }
                    .onDispose { disposed += "second:$it" }

                check(first == "first" && second == "second")
                handler { executions++ }
            }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, executions)
        assertEquals(listOf("second:second", "first:first"), disposed)
        worker.stop()
    }

    @Test
    fun `simple handlers can manage resources`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var disposed: String? = null
        var executions = 0
        val worker = worker(clock, store) {
            job<Unit> { _, _ ->
                val value by resource { "simple" }
                    .onDispose { disposed = it }
                check(value == "simple")
                executions++
            }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, executions)
        assertEquals("simple", disposed)
        worker.stop()
    }

    @Test
    fun `stopping before execution disposes prepared resources and releases the job`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var disposed = false
        val worker = worker(clock, store, prepareWindow = 1.minutes) {
            prepared<Unit> { _, _ ->
                resource { "prepared" }.onDispose { disposed = true }
                handler { }
            }
        }

        worker.schedule("job", Unit) { once(Instant.fromEpochMilliseconds(60_000)) }
        worker.start(backgroundScope)
        runCurrent()
        assertEquals(false, disposed)

        worker.stop()

        assertTrue(disposed)
        assertEquals(QueuedJob.Status.PENDING, assertNotNull(store.get(Job.Id("job"))).status)
    }

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
            retry = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0),
            poll = 1.seconds,
            prepareWindow = 30.seconds,
        ) {
            prepared<Unit> { _, _ ->
                val value by resource { "attempt" }.onDispose { disposed += it }
                check(value == "attempt")
                attempts++
                if (attempts == 1) error("preparation failed")
                handler { executions++ }
            }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
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
            retry = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0),
            poll = 1.seconds,
            prepareWindow = 30.seconds,
        ) {
            job<Unit> { _, _ ->
                attempts++
                if (attempts == 1) error("boom")
            }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
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
            retry = RetryPolicy(maxAttempts = 2, initialDelay = 10.seconds, jitter = 0.0),
        ) {
            job<Unit> { _, _ ->
                attempts++
                if (attempts == 1) error("boom")
            }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
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
    fun `handler errors are retried like exceptions`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var attempts = 0
        val worker = worker(
            clock = clock,
            store = store,
            retry = RetryPolicy(maxAttempts = 2, initialDelay = 10.seconds, jitter = 0.0),
        ) {
            job<Unit> { _, _ ->
                attempts++
                if (attempts == 1) throw AssertionError("boom")
            }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
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
    fun `recurring job schedules its next occurrence`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> runs++ }
        }

        worker.schedule("job", Unit) { every(20.seconds) }
        worker.start(backgroundScope)

        advanceTimeBy(21.seconds)
        runCurrent()
        assertEquals(1, runs)

        val job = assertNotNull(store.get(Job.Id("job")))
        assertEquals(QueuedJob.Status.PENDING, job.status)
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
    fun `recurring job advances to its next occurrence after exhausting attempts`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        var runs = 0
        val worker = worker(clock, store, history = history) {
            job<Unit> { _, _ ->
                runs++
                error("boom")
            }
        }

        worker.schedule("job", Unit) {
            every(20.seconds)
            retry = RetryPolicy(maxAttempts = 1, initialDelay = 1.seconds, jitter = 0.0)
        }
        worker.start(backgroundScope)

        advanceTimeBy(21.seconds)
        runCurrent()
        assertEquals(1, runs)

        val job = assertNotNull(store.get(Job.Id("job")))
        assertEquals(QueuedJob.Status.PENDING, job.status)
        assertEquals(Instant.fromEpochMilliseconds(40_000), job.nextRun)
        val entry = assertNotNull(history.list().singleOrNull())
        assertEquals(JobHistory.Status.FAILED, entry.status)
        assertEquals("Attempts exhausted: boom", entry.message)

        worker.stop()
    }

    @Test
    fun `expired lease is retried after the configured delay`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        val handlerId = serializer<Unit>().descriptor.serialName
        val retry = RetryPolicy(maxAttempts = 3, initialDelay = 3.seconds, jitter = 0.0)
        val worker = worker(
            clock = clock,
            store = store,
            history = history,
            retry = retry,
            lease = 5.seconds,
            heartbeat = 1.seconds,
            poll = 1.seconds,
        ) {
            job<Unit> { _, _ -> }
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
    fun `late one-shot job fails when its tolerance is exceeded`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        var runs = 0
        val worker = worker(clock, store, history = history) {
            job<Unit> { _, _ -> runs++ }
        }

        worker.schedule("job", Unit) {
            once(Instant.fromEpochMilliseconds(0))
            latePolicy = LateExecutionPolicy.FailAfter(5.seconds)
        }

        advanceTimeBy(10.seconds)
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(0, runs)
        assertNull(store.get(Job.Id("job")))
        assertEquals(JobHistory.Status.FAILED, assertNotNull(history.list().singleOrNull()).status)
        worker.stop()
    }

    @Test
    fun `retry that would exceed the late tolerance fails the job instead`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        var attempts = 0
        val worker = worker(
            clock = clock,
            store = store,
            history = history,
            retry = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0),
        ) {
            job<Unit> { _, _ ->
                attempts++
                error("boom")
            }
        }

        worker.schedule("job", Unit) {
            once(clock.now())
            latePolicy = LateExecutionPolicy.FailAfter(5.seconds)
        }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, attempts)
        assertNull(store.get(Job.Id("job")))
        assertEquals(
            "Next attempt would start 10s after its scheduled instant: boom",
            assertNotNull(history.list().singleOrNull()).message,
        )
        worker.stop()
    }

    @Test
    fun `retry within the late tolerance runs as scheduled`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var attempts = 0
        val worker = worker(
            clock = clock,
            store = store,
            retry = RetryPolicy(maxAttempts = 2, initialDelay = 10.seconds, jitter = 0.0),
        ) {
            job<Unit> { _, _ ->
                attempts++
                if (attempts == 1) error("boom")
            }
        }

        worker.schedule("job", Unit) {
            once(clock.now())
            latePolicy = LateExecutionPolicy.FailAfter(30.seconds)
        }
        worker.start(backgroundScope)
        runCurrent()
        assertEquals(1, attempts)

        advanceTimeBy(11.seconds)
        runCurrent()
        assertEquals(2, attempts)
        assertNull(store.get(Job.Id("job")))
        worker.stop()
    }

    @Test
    fun `invalid payload fails the job without retrying`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            job<String> { _, _ -> runs++ }
        }

        store.create(
            Job(
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
        assertNull(store.get(Job.Id("invalid")))
        worker.stop()
    }

    @Test
    fun `shutdown releases an interrupted job for retry`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val started = CompletableDeferred<Unit>()
        val worker = worker(clock, store) {
            job<Unit> { _, _ ->
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
        assertEquals(QueuedJob.Status.PENDING, job.status)
        assertEquals(clock.now(), job.nextRun)
        worker.stop()
    }

    @Test
    fun `sync preserves runtime state and resets a changed schedule`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> }
        }

        val id = worker.schedule("job", Unit) { once(clock.now()) }
        val definition = assertNotNull(store.get(id)).definition
        val claim = assertNotNull(
            store.claim(
                JobQueue.ClaimRequest(
                    now = clock.now(),
                    supportedHandlerIds = setOf(definition.handlerId),
                    prepareWindow = 1.seconds,
                    lease = 1.minutes,
                    workerId = "other",
                ),
            ),
        )
        store.reschedule(claim, clock.now() + 10.seconds)

        worker.sync(definition, Unit)
        val preserved = assertNotNull(store.get(id))
        assertEquals(QueuedJob.Status.PENDING, preserved.status)
        assertEquals(1, preserved.attempt)

        val changed = worker.schedule("job", Unit) {
            every(1.minutes, startAt = clock.now() + 5.minutes)
        }
        assertEquals(id, changed)
        val updated = assertNotNull(store.get(id))
        assertEquals(QueuedJob.Status.PENDING, updated.status)
        assertEquals(0, updated.attempt)
        assertEquals(clock.now() + 5.minutes, updated.nextRun)
    }

    @Test
    fun `handlers are selected by config serial name`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var strings = 0
        var ints = 0
        val worker = worker(clock, store) {
            job<String> { _, _ -> strings++ }
            job<Int> { _, _ -> ints++ }
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
    fun `queue forgets jobs once they finish`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> runs++ }
        }

        worker.enqueue(Unit, at = clock.now())
        worker.enqueue(Unit, at = clock.now())
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(2, runs)
        assertTrue(store.list().isEmpty())
        worker.stop()
    }

    @Test
    fun `duplicate handler configs are rejected`() {
        val store = InMemoryJobQueue(TestJson)

        assertFailsWith<IllegalArgumentException> {
            Worker(id = "worker") {
                jobs(store) {
                    job<Unit> { _, _ -> }
                    job<Unit> { _, _ -> }
                }
            }
        }
    }

    @Test
    fun `handlers run against the store they are registered with`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val shared = InMemoryJobQueue(TestJson)
        val local = InMemoryJobQueue(TestJson)
        val sharedHistory = InMemoryJobHistory()
        val localHistory = InMemoryJobHistory()
        val executed = mutableListOf<String>()
        val worker = Worker(id = "worker") {
            jobs(shared, sharedHistory) {
                job<String> { _, config -> executed += config }
            }
            jobs(local, localHistory) {
                job<Int> { _, config -> executed += config.toString() }
            }
            config {
                pollInterval = 10.seconds
                prepareWindow = 10.seconds
                leaseDuration = 1.minutes
                heartbeatInterval = 20.seconds
                maxConcurrentJobs = 4
                shutdownGracePeriod = 1.seconds
                retry = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0)
                this.clock = clock
                dispatcher = StandardTestDispatcher(testScheduler)
            }
        }

        worker.enqueue("shared", at = clock.now())
        worker.enqueue(42, at = clock.now())

        assertEquals(setOf("kotlin.String"), shared.list().map { it.definition.handlerId }.toSet())
        assertEquals(setOf("kotlin.Int"), local.list().map { it.definition.handlerId }.toSet())

        worker.start(backgroundScope)
        runCurrent()

        assertEquals(setOf("shared", "42"), executed.toSet())
        assertEquals(2, executed.size)
        assertEquals(setOf("kotlin.String"), sharedHistory.list().map { it.handlerId }.toSet())
        assertEquals(setOf("kotlin.Int"), localHistory.list().map { it.handlerId }.toSet())
        assertTrue(shared.list().isEmpty())
        assertTrue(local.list().isEmpty())
        worker.stop()
    }

    @Test
    fun `a handler cannot be registered in two queues`() {
        val first = InMemoryJobQueue(TestJson)
        val second = InMemoryJobQueue(TestJson)

        assertFailsWith<IllegalArgumentException> {
            Worker(id = "worker") {
                jobs(first) { job<Unit> { _, _ -> } }
                jobs(second) { job<Unit> { _, _ -> } }
            }
        }
    }

    @Test
    fun `the same queue can be used by multiple job blocks`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val stringsHistory = InMemoryJobHistory()
        val intsHistory = InMemoryJobHistory()
        val executed = mutableListOf<String>()
        val worker = Worker(id = "worker") {
            jobs(store, stringsHistory) {
                job<String> { _, config -> executed += config }
            }
            jobs(store, intsHistory) {
                job<Int> { _, config -> executed += config.toString() }
            }
            config {
                pollInterval = 10.seconds
                prepareWindow = 10.seconds
                leaseDuration = 1.minutes
                heartbeatInterval = 20.seconds
                maxConcurrentJobs = 4
                shutdownGracePeriod = 1.seconds
                retry = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0)
                this.clock = clock
                dispatcher = StandardTestDispatcher(testScheduler)
            }
        }

        worker.enqueue("shared", at = clock.now())
        worker.enqueue(42, at = clock.now())
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(setOf("shared", "42"), executed.toSet())
        assertEquals(listOf("kotlin.String"), stringsHistory.list().map { it.handlerId })
        assertEquals(listOf("kotlin.Int"), intsHistory.list().map { it.handlerId })
        assertTrue(store.list().isEmpty())
        worker.stop()
    }

    @Test
    fun `handlers stay unique across blocks using the same queue`() {
        val store = InMemoryJobQueue(TestJson)

        assertFailsWith<IllegalArgumentException> {
            Worker(id = "worker") {
                jobs(store) { job<Unit> { _, _ -> } }
                jobs(store) { job<Unit> { _, _ -> } }
            }
        }
    }

    @Test
    fun `the same history can be shared across queues`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val first = InMemoryJobQueue(TestJson)
        val second = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        val worker = Worker(id = "worker") {
            jobs(first, history) { job<String> { _, _ -> } }
            jobs(second, history) { job<Int> { _, _ -> } }
            config {
                pollInterval = 10.seconds
                prepareWindow = 10.seconds
                leaseDuration = 1.minutes
                heartbeatInterval = 20.seconds
                maxConcurrentJobs = 4
                shutdownGracePeriod = 1.seconds
                retry = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0)
                this.clock = clock
                dispatcher = StandardTestDispatcher(testScheduler)
            }
        }

        worker.enqueue("first", at = clock.now())
        worker.enqueue(2, at = clock.now())
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(setOf("kotlin.String", "kotlin.Int"), history.list().map { it.handlerId }.toSet())
        worker.stop()
    }

    @Test
    fun `concurrency limit applies per queue`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val first = InMemoryJobQueue(TestJson)
        val second = InMemoryJobQueue(TestJson)
        val gate = CompletableDeferred<Unit>()
        var running = 0
        var peak = 0

        suspend fun runJob() {
            running++
            peak = maxOf(peak, running)
            gate.await()
            running--
        }

        val worker = Worker(id = "worker") {
            jobs(first) { job<String> { _, _ -> runJob() } }
            jobs(second) { job<Int> { _, _ -> runJob() } }
            config {
                pollInterval = 10.seconds
                prepareWindow = 10.seconds
                leaseDuration = 1.minutes
                heartbeatInterval = 20.seconds
                maxConcurrentJobs = 1
                shutdownGracePeriod = 1.seconds
                retry = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0)
                this.clock = clock
                dispatcher = StandardTestDispatcher(testScheduler)
            }
        }

        worker.enqueue("first", at = clock.now())
        worker.enqueue(2, at = clock.now())
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(2, peak)

        gate.complete(Unit)
        runCurrent()
        worker.stop()
    }

    @Test
    fun `concurrency limit applies per job block sharing a store`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val gate = CompletableDeferred<Unit>()
        var running = 0
        var peak = 0

        suspend fun runJob() {
            running++
            peak = maxOf(peak, running)
            gate.await()
            running--
        }

        val worker = Worker(id = "worker") {
            jobs(store) { job<String> { _, _ -> runJob() } }
            jobs(store) { job<Int> { _, _ -> runJob() } }
            config {
                pollInterval = 10.seconds
                prepareWindow = 10.seconds
                leaseDuration = 1.minutes
                heartbeatInterval = 20.seconds
                maxConcurrentJobs = 1
                shutdownGracePeriod = 1.seconds
                retry = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0)
                this.clock = clock
                dispatcher = StandardTestDispatcher(testScheduler)
            }
        }

        worker.enqueue("first", at = clock.now())
        worker.enqueue(2, at = clock.now())
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(2, peak)

        gate.complete(Unit)
        runCurrent()
        worker.stop()
    }

    private fun TestScope.worker(
        clock: Clock,
        store: JobQueue<*>,
        history: JobHistory? = null,
        retry: RetryPolicy = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0),
        lease: Duration = 1.minutes,
        heartbeat: Duration = 20.seconds,
        poll: Duration = 10.seconds,
        prepareWindow: Duration = poll,
        configureHandlers: JobsRegistry<*>.() -> Unit,
    ): Worker = Worker(id = "worker") {
        jobs(store, history) { configureHandlers() }
        config {
            pollInterval = poll
            this.prepareWindow = prepareWindow
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
