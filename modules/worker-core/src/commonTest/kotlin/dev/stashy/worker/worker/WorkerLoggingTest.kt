package dev.stashy.worker.worker

import dev.stashy.worker.TestJson
import dev.stashy.worker.WorkerLogger
import dev.stashy.worker.store.memory.InMemoryJobQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerLoggingTest {
    @Test
    fun `job lifecycle is logged`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val logs = mutableListOf<Pair<WorkerLogger.Level, String>>()
        val logger = WorkerLogger { level, message, _ -> logs += level to message }
        val worker = worker(clock, store, logger = logger) {
            job<Unit> { _, _ -> handler { } }
        }

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(
            listOf(
                WorkerLogger.Level.INFO to "Preparing job 'job' for attempt 1",
                WorkerLogger.Level.DEBUG to "Prepared job 'job' in 0s",
                WorkerLogger.Level.INFO to "Running job 'job' for attempt 1",
                WorkerLogger.Level.INFO to "Job 'job' finished in 0s",
            ),
            logs,
        )
        worker.stop()
    }
}
