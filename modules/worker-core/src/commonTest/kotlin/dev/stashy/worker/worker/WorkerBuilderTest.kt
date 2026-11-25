package dev.stashy.worker.worker

import dev.stashy.worker.TestJson
import dev.stashy.worker.Worker
import dev.stashy.worker.job.RetryPolicy
import dev.stashy.worker.store.memory.InMemoryJobHistory
import dev.stashy.worker.store.memory.InMemoryJobQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerBuilderTest {
    @Test
    fun `duplicate handler configs are rejected`() {
        val store = InMemoryJobQueue(TestJson)

        assertFailsWith<IllegalArgumentException> {
            Worker.Companion(id = "worker") {
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
        val worker = Worker.Companion(id = "worker") {
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
                    retry = RetryPolicy.Exponential(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0)
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
            Worker.Companion(id = "worker") {
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
        val worker = Worker.Companion(id = "worker") {
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
                    retry = RetryPolicy.Exponential(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0)
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
            Worker.Companion(id = "worker") {
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
        val worker = Worker.Companion(id = "worker") {
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
                    retry = RetryPolicy.Exponential(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0)
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
    fun `jobs blocks can override the worker defaults`() = runTest {
        val clock = SchedulerClock(testScheduler)
        val store = InMemoryJobQueue(TestJson)
        var prepared = 0
        val worker = Worker.Companion(id = "worker") {
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

        worker.schedule("job", Unit) { schedule = once(Instant.fromEpochMilliseconds(50_000)) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, prepared)
        worker.stop()
    }

    @Test
    fun `invalid block config is rejected`() {
        val store = InMemoryJobQueue(TestJson)

        assertFailsWith<IllegalArgumentException> {
            Worker.Companion(id = "worker") {
                jobs(store, config = { pollInterval = 1.minutes; prepareWindow = 10.seconds }) {
                    job<Unit> { _, _ -> handler { } }
                }
            }
        }
    }
}
