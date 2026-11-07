package dev.stashy.standard.internal

import kotlin.test.*

@OptIn(InternalStandardApi::class)
private class CustomUntracedException(message: String? = null) : UntracedException(message)

class UntracedExceptionTest {
    @Test
    fun `exposes its message`() {
        assertEquals("stop", CustomUntracedException("stop").message)
    }

    @Test
    fun `does not collect a stack trace`() {
        val trace = CustomUntracedException("stop").stackTraceToString()

        assertTrue(trace.contains("stop"))
        assertFalse(trace.contains("at "))
    }

    @Test
    fun `subclasses also skip the stack trace`() {
        assertFalse(CustomUntracedException().stackTraceToString().contains("at "))
    }

    @Test
    fun `can be caught as an early return`() {
        val exception = assertFailsWith<UntracedException> {
            throw CustomUntracedException("stop")
        }

        assertEquals("stop", exception.message)
    }
}
