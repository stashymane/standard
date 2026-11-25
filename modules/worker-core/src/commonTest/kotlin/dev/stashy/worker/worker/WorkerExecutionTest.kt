package dev.stashy.worker.worker

import dev.stashy.worker.TestJson
import dev.stashy.worker.Worker
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.QueuedJob
import dev.stashy.worker.store.JobHistory
import dev.stashy.worker.store.JobQueue
import dev.stashy.worker.store.memory.InMemoryJobHistory
import dev.stashy.worker.store.memory.InMemoryJobQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerExecutionTest {
    @Test
    fun `worker executes a due job`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> handler { runs++ } }
        }

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
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

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
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
    fun `sync preserves runtime state and resets a changed schedule`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> handler { } }
        }

        val id = worker.schedule("job", Unit) { schedule = once(clock.now()) }
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
            schedule = every(1.minutes, startAt = clock.now() + 5.minutes)
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
}
