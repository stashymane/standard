package dev.stashy.standard.internal

import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(InternalStandardApi::class)
private class JvmCustomUntracedException : UntracedException()

class UntracedExceptionJvmTest {
    @Test
    fun `does not allocate a stack trace`() {
        assertTrue(JvmCustomUntracedException().stackTrace.isEmpty())
    }
}
