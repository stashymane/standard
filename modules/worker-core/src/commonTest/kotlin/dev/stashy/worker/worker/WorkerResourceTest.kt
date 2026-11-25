package dev.stashy.worker.worker

import dev.stashy.worker.TestJson
import dev.stashy.worker.store.memory.InMemoryJobQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerResourceTest {
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

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
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

        worker.schedule("job", Unit) { schedule = once(clock.now()) }
        worker.start(backgroundScope)
        runCurrent()

        assertEquals(1, executions)
        assertEquals("simple", disposed)
        worker.stop()
    }
}
