package dev.stashy.data

import dev.stashy.data.source.combine
import dev.stashy.data.source.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DataSourceTest {
    @Test
    fun `data sources do not cache by default`() = runTest {
        var loads = 0
        val source = DataSource {
            loads++
            "value"
        }

        assertEquals("value", source.await().getOrThrow())
        assertEquals("value", source.await().getOrThrow())
        assertEquals(2, loads)
        assertNull(source.getOrNull())
    }

    @Test
    fun `plain source state emits loading then success`() = runTest {
        var loads = 0
        val source = DataSource {
            loads++
            "value"
        }

        assertEquals(
            listOf(DataSourceState.Loading, DataSourceState.Success("value")),
            source.state.toList(),
        )
        assertEquals(1, loads)
    }

    @Test
    fun `plain source state emits loading then failure`() = runTest {
        val source = DataSource<String> {
            error("boom")
        }

        val states = source.state.toList()
        assertEquals(DataSourceState.Loading, states[0])
        val failure = assertIs<DataSourceState.Failure<String>>(states[1])
        assertEquals("boom", failure.cause.message)
    }

    @Test
    fun `mapping does not cache intermediate results`() = runTest {
        var loads = 0
        var maps = 0
        val source = DataSource {
            loads++
            1
        }.map {
            maps++
            it * 2
        }

        assertEquals(2, source.await().getOrThrow())
        assertEquals(2, source.await().getOrThrow())
        assertEquals(2, loads)
        assertEquals(2, maps)
        assertNull(source.getOrNull())
    }

    @Test
    fun `mapping maps state`() = runTest {
        val source = DataSource { 1 }.map { it * 2 }

        assertEquals(
            DataSourceState.Success(2),
            source.state.first { it is DataSourceState.Success },
        )
    }

    @Test
    fun `combining maps state`() = runTest {
        val first = DataSource { 1 }
        val second = DataSource { 2 }
        val combined = combine(first, second) { a, b -> a + b }

        assertEquals(
            DataSourceState.Success(3),
            combined.state.first { it is DataSourceState.Success },
        )
    }

    @Test
    fun `await rethrows cancellation`() = runTest {
        val source = DataSource<String> {
            throw CancellationException("gone")
        }

        val cancelled = assertFailsWith<CancellationException> { source.await() }
        assertEquals("gone", cancelled.message)
    }
}
