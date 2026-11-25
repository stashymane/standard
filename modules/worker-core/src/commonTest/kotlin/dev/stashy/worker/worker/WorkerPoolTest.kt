package dev.stashy.worker.worker

import dev.stashy.worker.TestJson
import dev.stashy.worker.pool.pool
import dev.stashy.worker.store.memory.InMemoryJobQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.collections.plusAssign
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerPoolTest {
    @Test
    fun `each block admits through its own pool`() = runTest {
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

        val worker = workerWith(clock) {
            jobs(first, pool = pool { capacity = 1 }) { job<String> { _, _ -> handler { runJob() } } }
            jobs(second, pool = pool { capacity = 1 }) { job<Int> { _, _ -> handler { runJob() } } }
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
    fun `a pool is shared by the jobs in a block`() = runTest {
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

        val worker = workerWith(clock) {
            jobs(store, pool = pool { capacity = 1 }) {
                job<String> { _, _ -> handler { runJob() } }
                job<Int> { _, _ -> handler { runJob() } }
            }
        }

        worker.enqueue("first", at = clock.now())
        worker.enqueue(2, at = clock.now())
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, peak)

        gate.complete(Unit)
        runCurrent()
        worker.stop()
    }

    @Test
    fun `a pool is shared across blocks`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val shared = pool("shared") { capacity = 1 }
        val gate = CompletableDeferred<Unit>()
        val executed = mutableListOf<String>()
        var running = 0
        var peak = 0

        suspend fun runJob(config: String) {
            executed += config
            running++
            peak = maxOf(peak, running)
            gate.await()
            running--
        }

        val worker = workerWith(clock) {
            jobs(store, pool = shared) { job<String> { _, config -> handler { runJob(config) } } }
            jobs(store, pool = shared) { job<Int> { _, config -> handler { runJob(config.toString()) } } }
        }

        worker.enqueue("first", at = clock.now())
        worker.enqueue(2, at = clock.now())
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, peak)
        assertEquals(1, executed.size)

        gate.complete(Unit)
        runCurrent()

        assertEquals(1, peak)
        assertEquals(2, executed.size)
        worker.stop()
    }

    @Test
    fun `a job can use its own pool`() = runTest {
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

        val worker = workerWith(clock) {
            jobs(store, pool = pool { capacity = 1 }) {
                job<String> { _, _ -> handler { runJob() } }
                job<Int>(pool = pool { capacity = 1 }) { _, _ -> handler { runJob() } }
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
    fun `a pool permit is released when a job is cancelled`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val started = CompletableDeferred<Unit>()
        val executed = mutableListOf<String>()
        val worker = workerWith(clock) {
            jobs(store, pool = pool { capacity = 1 }) {
                job<String> { _, config ->
                    handler {
                        executed += config
                        if (config == "first") {
                            started.complete(Unit)
                            CompletableDeferred<Unit>().await()
                        }
                    }
                }
            }
        }

        val id = worker.enqueue("first", at = clock.now())
        worker.start(backgroundScope)
        runCurrent()
        assertTrue(started.isCompleted)

        worker.enqueue("second", at = clock.now())
        advanceTimeBy(10.seconds)
        runCurrent()
        assertEquals(listOf("first"), executed)

        assertTrue(worker.cancel(id))
        runCurrent()

        assertEquals(listOf("first", "second"), executed)
        worker.stop()
    }

    @Test
    fun `a job keeps its permit while it waits for its instant`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val at = clock.now() + 5.seconds
        val executed = mutableListOf<String>()
        var prepared = 0
        val worker = workerWith(clock) {
            jobs(store, pool = pool { capacity = 1 }) {
                job<String> { _, config ->
                    prepared++
                    handler { executed += config }
                }
            }
        }

        worker.schedule("first", "first") { schedule = once(at) }
        worker.schedule("second", "second") { schedule = once(at) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, prepared)

        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(listOf("first"), executed)

        advanceTimeBy(10.seconds)
        runCurrent()

        assertEquals(2, prepared)
        assertEquals(listOf("first", "second"), executed)
        worker.stop()
    }

    @Test
    fun `surplus permits are returned when fewer jobs are due`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val gate = CompletableDeferred<Unit>()
        var running = 0
        var peak = 0

        suspend fun runJob(config: String) {
            running++
            peak = maxOf(peak, running)
            if (config != "warmup") gate.await()
            running--
        }

        val worker = workerWith(clock) {
            jobs(store, pool = pool { capacity = 2 }) {
                job<String> { _, config -> handler { runJob(config) } }
            }
        }

        worker.enqueue("warmup", at = clock.now())
        worker.start(backgroundScope)
        runCurrent()
        assertEquals(1, peak)

        peak = 0
        worker.enqueue("first", at = clock.now())
        worker.enqueue("second", at = clock.now())
        advanceTimeBy(10.seconds)
        runCurrent()

        assertEquals(2, peak)

        gate.complete(Unit)
        runCurrent()
        worker.stop()
    }

    @Test
    fun `a custom pool decides admission`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        val custom = CountingPool(capacity = 1)
        val executed = mutableListOf<String>()
        val worker = workerWith(clock) {
            jobs(store, pool = custom) {
                job<String> { _, config -> handler { executed += config } }
            }
        }

        worker.enqueue("first", at = clock.now())
        worker.enqueue("second", at = clock.now())
        worker.start(backgroundScope)
        runCurrent()
        advanceTimeBy(10.seconds)
        runCurrent()

        assertEquals(listOf("first", "second"), executed)
        assertEquals(2, custom.granted)
        assertEquals(2, custom.released)
        worker.stop()
    }
}
