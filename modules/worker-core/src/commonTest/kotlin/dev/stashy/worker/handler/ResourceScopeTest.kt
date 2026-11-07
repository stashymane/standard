package dev.stashy.worker.handler

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResourceScopeTest {
    private class DelegatingScope : ResourceScope by ResourceScope()

    @Test
    fun `disposes resources and defers in reverse order`() = runTest {
        val scope = DelegatingScope()
        val disposed = mutableListOf<String>()

        val first by scope.resource { "first" }.onDispose { disposed += "first:$it" }
        scope.defer { disposed += "defer" }
        val second by scope.resource { "second" }.onDispose { disposed += "second:$it" }

        scope.dispose()

        assertEquals(listOf("second:second", "defer", "first:first"), disposed)
    }

    @Test
    fun `lambda scope disposes its resources and returns the block result`() = runTest {
        val disposed = mutableListOf<String>()

        val result = ResourceScope {
            val first by resource { "first" }.onDispose { disposed += "first:$it" }
            defer { disposed += "defer" }
            val second by resource { "second" }.onDispose { disposed += "second:$it" }
            "result"
        }

        assertEquals("result", result)
        assertEquals(listOf("second:second", "defer", "first:first"), disposed)
    }

    @Test
    fun `lambda scope disposes its resources when the block fails`() = runTest {
        val disposed = mutableListOf<String>()

        val failure = assertFailsWith<IllegalStateException> {
            ResourceScope {
                resource { "value" }.onDispose { disposed += it }
                error("boom")
            }
        }

        assertEquals("boom", failure.message)
        assertEquals(listOf("value"), disposed)
    }

    @Test
    fun `lambda scope suppresses disposal failures when the block fails`() = runTest {
        val failure = assertFailsWith<IllegalStateException> {
            ResourceScope {
                defer { throw IllegalArgumentException("dispose") }
                error("boom")
            }
        }

        assertEquals("boom", failure.message)
        assertEquals(listOf("dispose"), failure.suppressedExceptions.map { it.message })
    }
}
