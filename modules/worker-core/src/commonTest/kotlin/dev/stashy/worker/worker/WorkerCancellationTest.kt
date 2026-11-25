package dev.stashy.worker.worker

import dev.stashy.worker.TestJson
import dev.stashy.worker.create
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.QueuedJob
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
class WorkerCancellationTest {
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

        val id = worker.schedule("job", Unit) { schedule = once(Instant.fromEpochMilliseconds(60_000)) }
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

        val id = worker.schedule("job", Unit) { schedule = once(clock.now() + 1.minutes) }
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

        val id = worker.schedule("job", Unit) { schedule = once(clock.now()) }
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

        val id = worker.schedule("job", Unit) { schedule = once(clock.now()) }
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

        val id = worker.schedule("job", Unit) { schedule = every(20.seconds) }
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
}
