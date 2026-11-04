package dev.stashy.worker

import dev.stashy.worker.handler.JobsRegistry
import dev.stashy.worker.handler.TerminalOutcome
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.LateExecutionPolicy
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
            job<Unit> { _, _ -> handler { runs++ } }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, runs)
        worker.stop()
    }

    @Test
    fun `jobs can access their worker and enqueue follow-up work`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val executed = mutableListOf<String>()
        var contextWorker: Worker? = null
        val worker = worker(clock, store) {
            job<String> { context, config ->
                handler {
                    contextWorker = context.worker
                    executed += config
                    if (config == "first") context.worker.enqueue("second", at = clock.now())
                }
            }
        }

        worker.enqueue("first", at = clock.now())
        worker.start(backgroundScope)
        runCurrent()
        assertEquals(listOf("first"), executed)

        advanceTimeBy(10.seconds)
        runCurrent()
        assertEquals(listOf("first", "second"), executed)
        assertSame(worker, contextWorker)
        worker.stop()
    }

    @Test
    fun `finished attempts are recorded in history`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        var runs = 0
        val worker = worker(clock, store, history = history) {
            job<Unit> { _, _ -> handler { runs++ } }
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
            job<Unit> { _, _ -> handler { runs++ } }
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
            job<Unit> { _, _ ->
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
            job<Unit> { _, _ ->
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
    fun `job blocks can manage resources`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var disposed: String? = null
        var executions = 0
        val worker = worker(clock, store) {
            job<Unit> { _, _ ->
                val value by resource { "simple" }
                    .onDispose { disposed = it }
                check(value == "simple")
                handler { executions++ }
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
            job<Unit> { _, _ ->
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
            job<Unit> { _, _ ->
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
                handler {
                    attempts++
                    if (attempts == 1) error("boom")
                }
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
                handler {
                    attempts++
                    if (attempts == 1) error("boom")
                }
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
                handler {
                    attempts++
                    if (attempts == 1) throw AssertionError("boom")
                }
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
            job<Unit> { _, _ -> handler { runs++ } }
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
                handler {
                    runs++
                    error("boom")
                }
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
    fun `late one-shot job fails when its tolerance is exceeded`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        var runs = 0
        val worker = worker(clock, store, history = history) {
            job<Unit> { _, _ -> handler { runs++ } }
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
                handler {
                    attempts++
                    error("boom")
                }
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
                handler {
                    attempts++
                    if (attempts == 1) error("boom")
                }
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
    fun `worker fails when a queue throws an error`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = object : JobQueue<String> by InMemoryJobQueue(TestJson) {
            override suspend fun claim(
                request: JobQueue.ClaimRequest,
                limit: Int,
            ): List<JobQueue.Claimed<String>> = throw AssertionError("boom")
        }
        val worker = worker(clock, store) { job<Unit> { _, _ -> handler { } } }
        val failure = CompletableDeferred<Throwable?>()

        val workerJob = worker.start(scope())
        workerJob.invokeOnCompletion { failure.complete(it) }
        runCurrent()

        assertTrue(workerJob.isCancelled)
        assertTrue(failure.await() is AssertionError)
    }

    @Test
    fun `worker fails when an execution throws an error`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val worker = worker(clock, store) {
            job<Unit> { _, _ ->
                resource { Unit }.onDispose { throw AssertionError("boom") }
                handler { }
            }
        }
        val failure = CompletableDeferred<Throwable?>()

        worker.schedule("job", Unit) { once(clock.now()) }
        val workerJob = worker.start(scope())
        workerJob.invokeOnCompletion { failure.complete(it) }
        runCurrent()

        assertTrue(workerJob.isCancelled)
        assertTrue(failure.await() is AssertionError)
    }

    @Test
    fun `startSuspending suspends until the worker stops`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val worker = worker(clock, store) { job<Unit> { _, _ -> handler { } } }

        val runner = launch { worker.startSuspending() }
        runCurrent()
        assertTrue(runner.isActive)

        worker.requestStop()
        runCurrent()
        assertTrue(runner.isCompleted)
    }

    @Test
    fun `startSuspending rethrows worker failures`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = object : JobQueue<String> by InMemoryJobQueue(TestJson) {
            override suspend fun claim(
                request: JobQueue.ClaimRequest,
                limit: Int,
            ): List<JobQueue.Claimed<String>> = throw AssertionError("boom")
        }
        val worker = worker(clock, store) { job<Unit> { _, _ -> handler { } } }

        assertFailsWith<AssertionError> { worker.startSuspending() }
    }

    @Test
    fun `stopping the worker does not fail its job`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val worker = worker(clock, store) { job<Unit> { _, _ -> handler { } } }
        val failure = CompletableDeferred<Throwable?>()

        val workerJob = worker.start(scope())
        workerJob.invokeOnCompletion { failure.complete(it) }
        runCurrent()
        worker.requestStop()
        runCurrent()

        assertTrue(workerJob.isCancelled)
        assertTrue(failure.await() is CancellationException)
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
                handler {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await()
                }
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
            job<Unit> { _, _ -> handler { } }
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
            job<String> { _, _ -> handler { strings++ } }
            job<Int> { _, _ -> handler { ints++ } }
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
            job<Unit> { _, _ -> handler { runs++ } }
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
                    job<Unit> { _, _ -> handler { } }
                    job<Unit> { _, _ -> handler { } }
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
                job<String> { _, config -> handler { executed += config } }
            }
            jobs(local, localHistory) {
                job<Int> { _, config -> handler { executed += config.toString() } }
            }
            config {
                shutdownGracePeriod = 1.seconds
                this.clock = clock
                dispatcher = StandardTestDispatcher(testScheduler)
                defaults {
                    pollInterval = 10.seconds
                    prepareWindow = 10.seconds
                    leaseDuration = 1.minutes
                    heartbeatInterval = 20.seconds
                    maxConcurrentJobs = 4
                    retry = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0)
                }
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
                jobs(first) { job<Unit> { _, _ -> handler { } } }
                jobs(second) { job<Unit> { _, _ -> handler { } } }
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
                job<String> { _, config -> handler { executed += config } }
            }
            jobs(store, intsHistory) {
                job<Int> { _, config -> handler { executed += config.toString() } }
            }
            config {
                shutdownGracePeriod = 1.seconds
                this.clock = clock
                dispatcher = StandardTestDispatcher(testScheduler)
                defaults {
                    pollInterval = 10.seconds
                    prepareWindow = 10.seconds
                    leaseDuration = 1.minutes
                    heartbeatInterval = 20.seconds
                    maxConcurrentJobs = 4
                    retry = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0)
                }
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
                jobs(store) { job<Unit> { _, _ -> handler { } } }
                jobs(store) { job<Unit> { _, _ -> handler { } } }
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
            jobs(first, history) { job<String> { _, _ -> handler { } } }
            jobs(second, history) { job<Int> { _, _ -> handler { } } }
            config {
                shutdownGracePeriod = 1.seconds
                this.clock = clock
                dispatcher = StandardTestDispatcher(testScheduler)
                defaults {
                    pollInterval = 10.seconds
                    prepareWindow = 10.seconds
                    leaseDuration = 1.minutes
                    heartbeatInterval = 20.seconds
                    maxConcurrentJobs = 4
                    retry = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0)
                }
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
            jobs(first) { job<String> { _, _ -> handler { runJob() } } }
            jobs(second) { job<Int> { _, _ -> handler { runJob() } } }
            config {
                shutdownGracePeriod = 1.seconds
                this.clock = clock
                dispatcher = StandardTestDispatcher(testScheduler)
                defaults {
                    pollInterval = 10.seconds
                    prepareWindow = 10.seconds
                    leaseDuration = 1.minutes
                    heartbeatInterval = 20.seconds
                    maxConcurrentJobs = 1
                    retry = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0)
                }
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
            jobs(store) { job<String> { _, _ -> handler { runJob() } } }
            jobs(store) { job<Int> { _, _ -> handler { runJob() } } }
            config {
                shutdownGracePeriod = 1.seconds
                this.clock = clock
                dispatcher = StandardTestDispatcher(testScheduler)
                defaults {
                    pollInterval = 10.seconds
                    prepareWindow = 10.seconds
                    leaseDuration = 1.minutes
                    heartbeatInterval = 20.seconds
                    maxConcurrentJobs = 1
                    retry = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0)
                }
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
    fun `cancelling a pending job removes it and records a cancellation`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        var runs = 0
        val worker = worker(clock, store, history = history) {
            job<Unit> { _, _ -> handler { runs++ } }
        }

        val id = worker.enqueue(Unit, at = clock.now() + 1.minutes)
        assertTrue(worker.cancel(id))

        assertNull(store.get(id))
        val entry = assertNotNull(history.list().singleOrNull())
        assertEquals(JobHistory.Status.CANCELLED, entry.status)
        assertEquals(0, entry.attempt)
        assertEquals(clock.now(), entry.finishedAt)
        assertEquals(0, runs)
    }

    @Test
    fun `cancelling an unknown job returns false`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> handler { } }
        }

        assertFalse(worker.cancel(Job.Id("missing")))
    }

    @Test
    fun `cancelling a prepared job waiting for its instant disposes its resources`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        var disposed = false
        var executions = 0
        val worker = worker(clock, store, history = history, prepareWindow = 1.minutes) {
            job<Unit> { _, _ ->
                resource { "prepared" }.onDispose { disposed = true }
                handler { executions++ }
            }
        }

        val id = worker.schedule("job", Unit) { once(Instant.fromEpochMilliseconds(60_000)) }
        worker.start(backgroundScope)
        runCurrent()
        assertFalse(disposed)

        assertTrue(worker.cancel(id))
        runCurrent()

        assertTrue(disposed)
        assertEquals(0, executions)
        assertNull(store.get(id))
        val entry = assertNotNull(history.list().singleOrNull())
        assertEquals(JobHistory.Status.CANCELLED, entry.status)
        assertEquals(1, entry.attempt)
        worker.stop()
    }

    @Test
    fun `cancelling a job during preparation disposes its resources`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        val preparing = CompletableDeferred<Unit>()
        var disposed = false
        var executions = 0
        val worker = worker(clock, store, history = history, prepareWindow = 1.minutes) {
            job<Unit> { _, _ ->
                resource { "prepared" }.onDispose { disposed = true }
                preparing.complete(Unit)
                CompletableDeferred<Unit>().await()
                handler { executions++ }
            }
        }

        val id = worker.schedule("job", Unit) { once(clock.now() + 1.minutes) }
        worker.start(backgroundScope)
        runCurrent()
        assertTrue(preparing.isCompleted)

        assertTrue(worker.cancel(id))
        runCurrent()

        assertTrue(disposed)
        assertEquals(0, executions)
        assertNull(store.get(id))
        assertEquals(
            JobHistory.Status.CANCELLED,
            assertNotNull(history.list().singleOrNull()).status,
        )
        worker.stop()
    }

    @Test
    fun `cancelling an executing job disposes its resources`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        val started = CompletableDeferred<Unit>()
        var disposed = false
        val worker = worker(clock, store, history = history) {
            job<Unit> { _, _ ->
                resource { "running" }.onDispose { disposed = true }
                handler {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await()
                }
            }
        }

        val id = worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()
        assertTrue(started.isCompleted)

        assertTrue(worker.cancel(id))
        runCurrent()

        assertTrue(disposed)
        assertNull(store.get(id))
        val entry = assertNotNull(history.list().singleOrNull())
        assertEquals(JobHistory.Status.CANCELLED, entry.status)
        assertEquals(1, entry.attempt)
        worker.stop()
    }

    @Test
    fun `a cancelled handler that finishes without suspending is recorded as cancelled`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        var runs = 0
        val worker = worker(clock, store, history = history) {
            job<Unit> { context, _ ->
                handler {
                    context.worker.cancel(context.jobId)
                    runs++
                }
            }
        }

        val id = worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, runs)
        assertNull(store.get(id))
        assertEquals(
            JobHistory.Status.CANCELLED,
            assertNotNull(history.list().singleOrNull()).status,
        )
        worker.stop()
    }

    @Test
    fun `a cancellation requested while the owner is gone is applied by the sweeper`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        val handlerId = serializer<Unit>().descriptor.serialName
        val worker = worker(
            clock = clock,
            store = store,
            history = history,
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
        assertEquals(JobQueue.CancellationResult.FLAGGED, store.requestCancellation(definition.id))

        worker.start(backgroundScope)
        advanceTimeBy(6.seconds)
        runCurrent()

        assertNull(store.get(definition.id))
        assertEquals(
            JobHistory.Status.CANCELLED,
            assertNotNull(history.list().singleOrNull()).status,
        )
        worker.stop()
    }

    @Test
    fun `a cancellation requested during execution prevents the next occurrence`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        val gate = CompletableDeferred<Unit>()
        val running = CompletableDeferred<Unit>()
        var runs = 0
        val worker = worker(clock, store, history = history) {
            job<Unit> { _, _ ->
                handler {
                    runs++
                    running.complete(Unit)
                    gate.await()
                }
            }
        }

        val id = worker.schedule("job", Unit) { every(20.seconds) }
        worker.start(backgroundScope)
        advanceTimeBy(21.seconds)
        runCurrent()
        assertEquals(1, runs)
        assertTrue(running.isCompleted)

        assertEquals(JobQueue.CancellationResult.FLAGGED, store.requestCancellation(id))
        gate.complete(Unit)
        runCurrent()

        val pending = assertNotNull(store.get(id))
        assertTrue(pending.cancelRequested)
        assertEquals(QueuedJob.Status.PENDING, pending.status)

        advanceTimeBy(10.seconds)
        runCurrent()

        assertNull(store.get(id))
        assertEquals(1, runs)
        assertEquals(
            JobHistory.Status.CANCELLED,
            assertNotNull(history.list().firstOrNull()).status,
        )
        worker.stop()
    }

    @Test
    fun `finalize reports a completed one-shot job`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val configs = mutableListOf<String>()
        val outcomes = mutableListOf<TerminalOutcome>()
        val worker = worker(clock, store) {
            job<String>() { _, config ->
                handler {} finalizedBy { outcome ->
                    configs += config
                    outcomes += outcome
                }
            }
        }

        worker.schedule("job", "payload") { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(listOf("payload"), configs)
        assertEquals(listOf<TerminalOutcome>(TerminalOutcome.Succeeded), outcomes)
        worker.stop()
    }

    @Test
    fun `finalizers can use prepared resources before they are disposed`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val events = mutableListOf<String>()
        val worker = worker(clock, store) {
            job<Unit> { _, _ ->
                val value by resource { "prepared" }.onDispose { events += "disposed:$it" }
                handler { events += "ran" } finalizedBy { outcome ->
                    events += "finalized:$value:$outcome"
                }
            }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(listOf("ran", "finalized:prepared:Succeeded", "disposed:prepared"), events)
        worker.stop()
    }

    @Test
    fun `finalize reports an exhausted job as failed`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val outcomes = mutableListOf<TerminalOutcome>()
        val worker = worker(
            clock = clock,
            store = store,
            retry = RetryPolicy(maxAttempts = 1, initialDelay = 1.seconds, jitter = 0.0),
        ) {
            job<Unit> { _, _ ->
                handler { error("boom") } finalizedBy { outcome -> outcomes += outcome }
            }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(
            listOf<TerminalOutcome>(TerminalOutcome.Failed("Attempts exhausted: boom")),
            outcomes,
        )
        worker.stop()
    }

    @Test
    fun `finalize reports a job that started too late as failed`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val outcomes = mutableListOf<TerminalOutcome>()
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> handler { } finalizedBy { outcome -> outcomes += outcome } }
        }

        worker.schedule("job", Unit) {
            once(Instant.fromEpochMilliseconds(0))
            latePolicy = LateExecutionPolicy.FailAfter(5.seconds)
        }
        advanceTimeBy(10.seconds)
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(
            listOf<TerminalOutcome>(TerminalOutcome.Failed("Job started 10s after its scheduled instant")),
            outcomes,
        )
        worker.stop()
    }

    @Test
    fun `finalize reports a retry that exceeded the late tolerance as failed`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val outcomes = mutableListOf<TerminalOutcome>()
        val worker = worker(
            clock = clock,
            store = store,
            retry = RetryPolicy(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0),
        ) {
            job<Unit> { _, _ ->
                handler { error("boom") } finalizedBy { outcome -> outcomes += outcome }
            }
        }

        worker.schedule("job", Unit) {
            once(clock.now())
            latePolicy = LateExecutionPolicy.FailAfter(5.seconds)
        }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(
            listOf<TerminalOutcome>(
                TerminalOutcome.Failed("Next attempt would start 10s after its scheduled instant: boom"),
            ),
            outcomes,
        )
        worker.stop()
    }

    @Test
    fun `finalize does not run for a cancelled pending job`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val outcomes = mutableListOf<TerminalOutcome>()
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> handler { } finalizedBy { outcome -> outcomes += outcome } }
        }

        val id = worker.enqueue(Unit, at = clock.now() + 1.minutes)
        assertTrue(worker.cancel(id))

        assertTrue(outcomes.isEmpty())
    }

    @Test
    fun `finalize reports a cancelled running job`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val started = CompletableDeferred<Unit>()
        val outcomes = mutableListOf<TerminalOutcome>()
        val worker = worker(clock, store) {
            job<Unit> { _, _ ->
                handler {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await()
                } finalizedBy { outcome -> outcomes += outcome }
            }
        }

        val id = worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()
        assertTrue(started.isCompleted)

        assertTrue(worker.cancel(id))
        runCurrent()

        assertEquals(listOf<TerminalOutcome>(TerminalOutcome.Cancelled), outcomes)
        worker.stop()
    }

    @Test
    fun `finalize does not run for a cancellation applied by the sweeper`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val handlerId = serializer<Unit>().descriptor.serialName
        val outcomes = mutableListOf<TerminalOutcome>()
        val worker = worker(
            clock = clock,
            store = store,
            lease = 5.seconds,
            heartbeat = 1.seconds,
            poll = 1.seconds,
        ) {
            job<Unit> { _, _ -> handler { } finalizedBy { outcome -> outcomes += outcome } }
        }

        val definition = Job(
            id = Job.Id("crashed"),
            handlerId = handlerId,
            schedule = Schedule.Once(clock.now()),
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
        assertEquals(JobQueue.CancellationResult.FLAGGED, store.requestCancellation(definition.id))

        worker.start(backgroundScope)
        advanceTimeBy(6.seconds)
        runCurrent()

        assertTrue(outcomes.isEmpty())
        worker.stop()
    }

    @Test
    fun `finalize does not run while a recurring job continues`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val outcomes = mutableListOf<TerminalOutcome>()
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> handler { } finalizedBy { outcome -> outcomes += outcome } }
        }

        worker.schedule("job", Unit) { every(20.seconds) }
        worker.start(backgroundScope)
        advanceTimeBy(21.seconds)
        runCurrent()

        assertTrue(outcomes.isEmpty())
        worker.stop()
    }

    @Test
    fun `finalize does not run when a recurring occurrence exhausts its attempts`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val outcomes = mutableListOf<TerminalOutcome>()
        val worker = worker(clock, store) {
            job<Unit> { _, _ ->
                handler { error("boom") } finalizedBy { outcome -> outcomes += outcome }
            }
        }

        worker.schedule("job", Unit) {
            every(20.seconds)
            retry = RetryPolicy(maxAttempts = 1, initialDelay = 1.seconds, jitter = 0.0)
        }
        worker.start(backgroundScope)
        advanceTimeBy(21.seconds)
        runCurrent()

        assertTrue(outcomes.isEmpty())
        worker.stop()
    }

    @Test
    fun `finalize does not run when shutdown releases a job`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val started = CompletableDeferred<Unit>()
        val outcomes = mutableListOf<TerminalOutcome>()
        val worker = worker(clock, store) {
            job<Unit> { _, _ ->
                handler {
                    started.complete(Unit)
                    CompletableDeferred<Unit>().await()
                } finalizedBy { outcome -> outcomes += outcome }
            }
        }

        worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()
        assertTrue(started.isCompleted)

        worker.stop()

        assertTrue(outcomes.isEmpty())
        assertNotNull(store.get(Job.Id("job")))
    }

    @Test
    fun `a failing finalize callback does not affect the job`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val history = InMemoryJobHistory()
        val worker = worker(clock, store, history = history) {
            job<Unit> { _, _ ->
                handler { } finalizedBy { throw AssertionError("boom") }
            }
        }

        val id = worker.schedule("job", Unit) { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertNull(store.get(id))
        assertEquals(JobHistory.Status.SUCCEEDED, assertNotNull(history.list().singleOrNull()).status)
        worker.stop()
    }

    @Test
    fun `finalize does not run when preparation fails`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var finalized = 0
        val worker = worker(clock, store) {
            job<String> { context, _ ->
                check(context.attempt < 0) { "preparation failed" }
                handler { } finalizedBy { finalized++ }
            }
        }

        worker.schedule("job", "config") { once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(0, finalized)
        worker.stop()
    }

    @Test
    fun `jobs blocks can override the worker defaults`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var prepared = 0
        val worker = Worker(id = "worker") {
            jobs(store, config = { prepareWindow = 1.minutes }) {
                job<Unit> { _, _ ->
                    prepared++
                    handler { }
                }
            }
            config {
                shutdownGracePeriod = 1.seconds
                this.clock = clock
                dispatcher = StandardTestDispatcher(testScheduler)
                defaults {
                    pollInterval = 10.seconds
                    prepareWindow = 10.seconds
                    leaseDuration = 1.minutes
                    heartbeatInterval = 20.seconds
                }
            }
        }

        worker.schedule("job", Unit) { once(Instant.fromEpochMilliseconds(50_000)) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, prepared)
        worker.stop()
    }

    @Test
    fun `enqueued jobs use their block's default retry policy`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val retry = RetryPolicy(maxAttempts = 9, initialDelay = 1.seconds, jitter = 0.0)
        val worker = Worker(id = "worker") {
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

    @Test
    fun `invalid block config is rejected`() {
        val store = InMemoryJobQueue(TestJson)

        assertFailsWith<IllegalArgumentException> {
            Worker(id = "worker") {
                jobs(store, config = { pollInterval = 1.minutes; prepareWindow = 10.seconds }) {
                    job<Unit> { _, _ -> handler { } }
                }
            }
        }
    }

    private fun TestScope.scope(): CoroutineScope = CoroutineScope(
        SupervisorJob() +
                StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, _ -> },
    )

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
            shutdownGracePeriod = 1.seconds
            this.clock = clock
            dispatcher = StandardTestDispatcher(testScheduler)
            defaults {
                pollInterval = poll
                this.prepareWindow = prepareWindow
                leaseDuration = lease
                heartbeatInterval = heartbeat
                maxConcurrentJobs = 4
                this.retry = retry
            }
        }
    }

    private class SchedulerClock(private val scheduler: TestCoroutineScheduler) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(scheduler.currentTime)
    }
}
