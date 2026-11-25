package dev.stashy.worker.worker

import dev.stashy.worker.TestJson
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.QueuedJob
import dev.stashy.worker.store.JobQueue
import dev.stashy.worker.store.memory.InMemoryJobQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerLifecycleTest {
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

        worker.schedule("job", Unit) { schedule = once(Instant.fromEpochMilliseconds(60_000)) }
        worker.start(backgroundScope)
        runCurrent()
        assertEquals(false, disposed)

        worker.stop()

        assertTrue(disposed)
        assertEquals(QueuedJob.Status.PENDING, assertNotNull(store.get(Job.Id("job"))).status)
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
                resource { }.onDispose { throw AssertionError("boom") }
                handler { }
            }
        }
        val failure = CompletableDeferred<Throwable?>()

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
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

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()
        assertTrue(started.isCompleted)

        worker.stop()

        val job = assertNotNull(store.get(Job.Id("job")))
        assertEquals(QueuedJob.Status.PENDING, job.status)
        assertEquals(clock.now(), job.nextRun)
        worker.stop()
    }
}
