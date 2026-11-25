package dev.stashy.worker.worker

import dev.stashy.worker.TestJson
import dev.stashy.worker.create
import dev.stashy.worker.handler.TerminalOutcome
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.LateExecutionPolicy
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerFinalizationTest {
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

        worker.schedule("job", "payload") { schedule = once(clock.now()) }
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

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
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
            retry = RetryPolicy.Exponential(maxAttempts = 1, initialDelay = 1.seconds, jitter = 0.0),
        ) {
            job<Unit> { _, _ ->
                handler { error("boom") } finalizedBy { outcome -> outcomes += outcome }
            }
        }

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
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
            schedule = once(Instant.fromEpochMilliseconds(0))
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
            retry = RetryPolicy.Exponential(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0),
        ) {
            job<Unit> { _, _ ->
                handler { error("boom") } finalizedBy { outcome -> outcomes += outcome }
            }
        }

        worker.schedule("job", Unit) {
            schedule = once(clock.now())
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

        val id = worker.schedule("job", Unit) { schedule = once(clock.now()) }
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

        worker.schedule("job", Unit) { schedule = every(20.seconds) }
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
            schedule = every(20.seconds)
            retry = RetryPolicy.Exponential(maxAttempts = 1, initialDelay = 1.seconds, jitter = 0.0)
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

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
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

        val id = worker.schedule("job", Unit) { schedule = once(clock.now()) }
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

        worker.schedule("job", "config") { schedule = once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(0, finalized)
        worker.stop()
    }
}
