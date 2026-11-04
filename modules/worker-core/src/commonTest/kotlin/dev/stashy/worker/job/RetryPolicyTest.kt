package dev.stashy.worker.job

import dev.stashy.worker.TestJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RetryPolicyTest {
    @Test
    fun `exponential retry delay grows exponentially without jitter`() {
        val policy = RetryPolicy.Exponential(
            maxAttempts = 4,
            initialDelay = 5.seconds,
            multiplier = 2.0,
            jitter = 0.0,
        )

        assertEquals(5.seconds, policy.delayFor(1))
        assertEquals(10.seconds, policy.delayFor(2))
        assertEquals(20.seconds, policy.delayFor(3))
        assertEquals(40.seconds, policy.delayFor(4))
    }

    @Test
    fun `delay retry waits the same duration every time`() {
        val policy = RetryPolicy.Delayed(maxAttempts = 4, delay = 5.seconds)

        assertEquals(5.seconds, policy.delayFor(1))
        assertEquals(5.seconds, policy.delayFor(2))
        assertEquals(5.seconds, policy.delayFor(3))
    }

    @Test
    fun `instant retry does not wait`() {
        val policy = RetryPolicy.Instant(maxAttempts = 4)

        assertEquals(0.seconds, policy.delayFor(1))
        assertEquals(0.seconds, policy.delayFor(2))
    }

    @Test
    fun `unlimited attempts round trip through json`() {
        val policy: RetryPolicy = RetryPolicy.Instant(maxAttempts = null)

        assertEquals(policy, TestJson.decodeFromString<RetryPolicy>(TestJson.encodeToString(policy)))
    }
}
