package dev.stashy.worker.worker

import dev.stashy.worker.TestJson
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.LateExecutionPolicy
import dev.stashy.worker.job.RetryPolicy
import dev.stashy.worker.store.JobHistory
import dev.stashy.worker.store.memory.InMemoryJobHistory
import dev.stashy.worker.store.memory.InMemoryJobQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerLateExecutionTest {
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
            schedule = once(Instant.fromEpochMilliseconds(0))
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
            retry = RetryPolicy.Exponential(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0),
        ) {
            job<Unit> { _, _ ->
                handler {
                    attempts++
                    error("boom")
                }
            }
        }

        worker.schedule("job", Unit) {
            schedule = once(clock.now())
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
            retry = RetryPolicy.Exponential(maxAttempts = 2, initialDelay = 10.seconds, jitter = 0.0),
        ) {
            job<Unit> { _, _ ->
                handler {
                    attempts++
                    if (attempts == 1) error("boom")
                }
            }
        }

        worker.schedule("job", Unit) {
            schedule = once(clock.now())
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
}
