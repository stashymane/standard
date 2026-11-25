package dev.stashy.worker.worker

import dev.stashy.worker.TestJson
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.LateExecutionPolicy
import dev.stashy.worker.job.QueuedJob
import dev.stashy.worker.job.RetryPolicy
import dev.stashy.worker.store.JobHistory
import dev.stashy.worker.store.memory.InMemoryJobHistory
import dev.stashy.worker.store.memory.InMemoryJobQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerScheduleTest {
    @Test
    fun `worker does not start a job before its instant`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> handler { runs++ } }
        }
        val at = Instant.fromEpochMilliseconds(60_000)

        worker.schedule("job", Unit) { schedule = once(at) }
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

        worker.schedule("job", Unit) { schedule = once(at) }
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
    fun `recurring job schedules its next occurrence`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> handler { runs++ } }
        }

        worker.schedule("job", Unit) { schedule = every(20.seconds) }
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
    fun `phased job switches cadence at its boundary`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val runs = mutableListOf<Instant>()
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> handler { runs += clock.now() } }
        }

        worker.schedule("job", Unit) {
            schedule = phased {
                until(Instant.fromEpochMilliseconds(30_000)) { every(10.seconds) }
                then { every(1.minutes) }
            }
        }
        worker.start(backgroundScope)

        advanceTimeBy(21.seconds)
        runCurrent()
        assertEquals(2, runs.size)

        advanceTimeBy(9.seconds)
        runCurrent()
        assertEquals(3, runs.size)

        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(
            listOf(10_000L, 20_000L, 30_000L, 90_000L).map { Instant.fromEpochMilliseconds(it) },
            runs,
        )
        worker.stop()
    }

    @Test
    fun `phased one-shot phase fails when it starts too late`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var runs = 0
        val worker = worker(clock, store) {
            job<Unit> { _, _ -> handler { runs++ } }
        }

        worker.schedule("job", Unit) {
            schedule = phased {
                until(Instant.fromEpochMilliseconds(3_600_000)) { once(Instant.fromEpochMilliseconds(0)) }
                then { every(1.minutes) }
            }
            latePolicy = LateExecutionPolicy.FailAfter(5.seconds)
        }

        advanceTimeBy(10.seconds)
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(0, runs)
        assertNull(store.get(Job.Id("job")))
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
            schedule = every(20.seconds)
            retry = RetryPolicy.Exponential(maxAttempts = 1, initialDelay = 1.seconds, jitter = 0.0)
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
}
