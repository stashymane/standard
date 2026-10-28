package dev.stashy.worker.job

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RetryPolicyTest {
    @Test
    fun `retry delay grows exponentially without jitter`() {
        val policy = RetryPolicy(maxAttempts = 4, initialDelay = 5.seconds, multiplier = 2.0, jitter = 0.0)

        assertEquals(5.seconds, policy.delayFor(1))
        assertEquals(10.seconds, policy.delayFor(2))
        assertEquals(20.seconds, policy.delayFor(3))
        assertEquals(40.seconds, policy.delayFor(4))
    }
}
