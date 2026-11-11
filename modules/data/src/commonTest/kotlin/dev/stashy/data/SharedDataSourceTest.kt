package dev.stashy.data

import dev.stashy.data.source.combine
import dev.stashy.data.source.map
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SharedDataSourceTest {
    @Test
    fun `shared source loads once`() = runTest {
        var loads = 0
        val source = sharedSource {
            loads++
            "value"
        }

        assertEquals("value", source.await().getOrThrow())
        assertEquals("value", source.await().getOrThrow())
        assertEquals("value", source.getOrNull())
        assertEquals(1, loads)
    }

    @Test
    fun `preload warms the cache`() = runTest {
        var loads = 0
        val source = sharedSource {
            loads++
            "value"
        }

        assertNull(source.getOrNull())
        source.preload()
        assertEquals("value", source.getOrNull())
        assertEquals(1, loads)

        source.preload()
        assertEquals(1, loads)
    }

    @Test
    fun `preload failure is swallowed`() = runTest {
        var loads = 0
        val source = sharedSource {
            loads++
            if (loads == 1) error("preload boom")
            "ok"
        }

        source.preload()
        assertNull(source.getOrNull())
        assertEquals(1, loads)

        assertEquals("ok", source.await().getOrThrow())
        assertEquals("ok", source.getOrNull())
        assertEquals(2, loads)
    }

    @Test
    fun `isRefreshing tracks load progress`() = runTest {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val source = sharedSource {
            started.complete(Unit)
            gate.await()
            "value"
        }

        assertFalse(source.isRefreshing.value)
        val load = async { source.await() }
        started.await()
        assertTrue(source.isRefreshing.value)

        gate.complete(Unit)
        assertEquals("value", load.await().getOrThrow())
        assertFalse(source.isRefreshing.value)
    }

    @Test
    fun `failure clears the in-flight load`() = runTest {
        var loads = 0
        val source = sharedSource {
            loads++
            error("boom")
        }

        val first = source.await()
        assertTrue(first.isFailure)
        assertEquals("boom", first.exceptionOrNull()?.message)
        assertNull(source.getOrNull())
        assertEquals(1, loads)

        val second = source.await()
        assertTrue(second.isFailure)
        assertEquals("boom", second.exceptionOrNull()?.message)
        assertNull(source.getOrNull())
        assertEquals(2, loads)
    }

    @Test
    fun `awaiting after failure retries and caches`() = runTest {
        var loads = 0
        val source = sharedSource {
            loads++
            if (loads == 1) error("temporary")
            "ok"
        }

        assertTrue(source.await().isFailure)
        assertNull(source.getOrNull())
        assertEquals(1, loads)

        assertEquals("ok", source.await().getOrThrow())
        assertEquals("ok", source.getOrNull())
        assertEquals(2, loads)

        assertEquals("ok", source.await().getOrThrow())
        assertEquals(2, loads)
    }

    @Test
    fun `map failure retries without caching`() = runTest {
        var loads = 0
        var maps = 0
        val source = dataSource {
            loads++
            "payload"
        }.map {
            maps++
            if (maps == 1) error("parse failed")
            it.uppercase()
        }.shared()

        val failed = source.await()
        assertTrue(failed.isFailure)
        assertEquals("parse failed", failed.exceptionOrNull()?.message)
        assertNull(source.getOrNull())
        assertEquals(1, loads)
        assertEquals(1, maps)

        assertEquals("PAYLOAD", source.await().getOrThrow())
        assertEquals("PAYLOAD", source.getOrNull())
        assertEquals(2, loads)
        assertEquals(2, maps)
    }

    @Test
    fun `concurrent awaits share one load`() = runTest {
        var loads = 0
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val source = sharedSource {
            loads++
            started.complete(Unit)
            gate.await()
            "shared"
        }

        coroutineScope {
            val first = async { source.await() }
            val second = async { source.await() }
            started.await()
            yield()
            gate.complete(Unit)
            assertEquals("shared", first.await().getOrThrow())
            assertEquals("shared", second.await().getOrThrow())
        }

        assertEquals(1, loads)
        assertEquals("shared", source.getOrNull())
    }

    @Test
    fun `concurrent failure propagates and allows retry`() = runTest {
        var loads = 0
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val source = sharedSource {
            loads++
            started.complete(Unit)
            gate.await()
            if (loads == 1) error("shared failure")
            "recovered"
        }

        coroutineScope {
            val first = async { source.await() }
            val second = async { source.await() }
            started.await()
            yield()
            gate.complete(Unit)

            val firstResult = first.await()
            val secondResult = second.await()
            assertTrue(firstResult.isFailure)
            assertTrue(secondResult.isFailure)
            assertEquals("shared failure", firstResult.exceptionOrNull()?.message)
            assertEquals("shared failure", secondResult.exceptionOrNull()?.message)
        }

        assertNull(source.getOrNull())
        assertEquals(1, loads)

        assertEquals("recovered", source.await().getOrThrow())
        assertEquals(2, loads)
        assertEquals("recovered", source.getOrNull())
    }

    @Test
    fun `cancellation clears the in-flight load`() = runTest {
        var loads = 0
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val source = sharedSource {
            loads++
            if (loads == 1) {
                started.complete(Unit)
                gate.await()
            }
            "ok"
        }

        val inFlight = async { source.await() }
        started.await()
        inFlight.cancel(CancellationException("stop"))
        assertFailsWith<CancellationException> { inFlight.await() }

        assertNull(source.getOrNull())
        assertEquals(1, loads)

        assertEquals("ok", source.await().getOrThrow())
        assertEquals(2, loads)
        assertEquals("ok", source.getOrNull())
    }

    @Test
    fun `cancellation reaches concurrent awaiters`() = runTest {
        var loads = 0
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val source = sharedSource {
            loads++
            if (loads == 1) {
                started.complete(Unit)
                gate.await()
            }
            "recovered"
        }

        val first = async { source.await() }
        started.await()
        yield()
        val second = async { source.await() }
        yield()

        first.cancel(CancellationException("stop"))
        assertFailsWith<CancellationException> { first.await() }
        assertFailsWith<CancellationException> { second.await() }

        assertNull(source.getOrNull())
        assertEquals(1, loads)

        assertEquals("recovered", source.await().getOrThrow())
        assertEquals(2, loads)
        assertEquals("recovered", source.getOrNull())
    }

    @Test
    fun `refresh updates the shared state`() = runTest {
        var loads = 0
        val source = sharedSource { ++loads }
        source.preload()

        val states = mutableListOf<DataSourceState<Int>>()
        val observer = backgroundScope.launch {
            source.state.collect { states += it }
        }
        runCurrent()

        source.refresh()
        runCurrent()

        assertEquals(DataSourceState.Success(2), source.state.value)
        assertEquals(2, source.getOrNull())
        assertEquals(2, loads)
        assertEquals(DataSourceState.Success(2), states.last())

        observer.cancel()
    }

    @Test
    fun `multiple observers see the same refresh`() = runTest {
        var loads = 0
        val source = sharedSource { ++loads }
        source.preload()

        val first = async { source.state.first { it.valueOrNull == 2 } }
        val second = async { source.state.first { it.valueOrNull == 2 } }

        source.refresh()

        assertEquals(2, first.await().valueOrNull)
        assertEquals(2, second.await().valueOrNull)
        assertEquals(2, loads)
    }

    @Test
    fun `refresh failure discards the previous value`() = runTest {
        var loads = 0
        val source = sharedSource {
            loads++
            if (loads == 2) error("boom")
            loads
        }
        source.preload()

        val result = source.refresh()

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
        assertNull(source.getOrNull())
        val failure = assertIs<DataSourceState.Failure<Int>>(source.state.value)
        assertEquals("boom", failure.cause.message)

        assertEquals(3, source.await().getOrThrow())
        assertEquals(3, source.getOrNull())
    }

    @Test
    fun `mapping a shared source updates its state`() = runTest {
        var loads = 0
        val base = sharedSource { ++loads }
        val mapped = base.map { it * 2 }

        base.preload()
        assertEquals(DataSourceState.Success(2), mapped.state.first())

        base.refresh()
        assertEquals(DataSourceState.Success(4), mapped.state.first())
    }

    @Test
    fun `combining shared sources updates state`() = runTest {
        var firstLoads = 0
        var secondLoads = 0
        val first = sharedSource { ++firstLoads }
        val second = sharedSource { ++secondLoads }
        val combined = combine(first, second) { a, b -> a + b }

        first.preload()
        second.preload()
        assertEquals(DataSourceState.Success(2), combined.state.first())

        first.refresh()
        assertEquals(DataSourceState.Success(3), combined.state.first())
    }

    private fun <T : Any> TestScope.sharedSource(load: suspend () -> T): SharedDataSource<T> =
        dataSource(load).shared()
}
