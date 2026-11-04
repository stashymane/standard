package dev.stashy.worker.job

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Controls retries after a job throws or its lease expires.
 */
@Serializable
public sealed interface RetryPolicy {
    /**
     * How many times to try again before giving up and failing the job.
     * This includes the initial execution. A null value retries indefinitely.
     */
    public val maxAttempts: Int?

    public fun delayFor(attempt: Int): Duration

    /**
     * Retries after a delay that grows exponentially with each attempt.
     */
    @Serializable
    @SerialName("exponential")
    public data class Exponential(
        override val maxAttempts: Int? = 5,
        public val initialDelay: Duration = 10.seconds,
        public val maxDelay: Duration = 10.minutes,
        public val multiplier: Double = 2.0,
        public val jitter: Double = 0.2,
    ) : RetryPolicy {
        init {
            validateMaxAttempts(maxAttempts)
            require(initialDelay.isFinite() && initialDelay >= Duration.ZERO) {
                "initialDelay must be a non-negative finite duration"
            }
            require(maxDelay.isFinite() && maxDelay >= initialDelay) {
                "maxDelay must be finite and not shorter than initialDelay"
            }
            require(multiplier.isFinite() && multiplier >= 1.0) {
                "multiplier must be a finite number of at least 1"
            }
            require(jitter in 0.0..<1.0) { "jitter must be in [0, 1)" }
        }

        override fun delayFor(attempt: Int): Duration {
            validateAttempt(attempt)
            val exponent = (attempt - 1).coerceAtMost(30)
            val delay = minOf(initialDelay * multiplier.pow(exponent.toDouble()), maxDelay)
            return if (jitter == 0.0) delay else delay * Random.nextDouble(1.0 - jitter, 1.0 + jitter)
        }
    }

    /**
     * Retries after the same [delay] every time.
     */
    @Serializable
    @SerialName("delayed")
    public data class Delayed(
        override val maxAttempts: Int? = 5,
        public val delay: Duration = 10.seconds,
    ) : RetryPolicy {
        init {
            validateMaxAttempts(maxAttempts)
            require(delay.isFinite() && delay >= Duration.ZERO) {
                "delay must be a non-negative finite duration"
            }
        }

        override fun delayFor(attempt: Int): Duration {
            validateAttempt(attempt)
            return delay
        }
    }

    /**
     * Retries without waiting.
     */
    @Serializable
    @SerialName("instant")
    public data class Instant(
        override val maxAttempts: Int? = 5,
    ) : RetryPolicy {
        init {
            validateMaxAttempts(maxAttempts)
        }

        override fun delayFor(attempt: Int): Duration {
            validateAttempt(attempt)
            return Duration.ZERO
        }
    }
}

private fun validateMaxAttempts(maxAttempts: Int?) {
    require(maxAttempts == null || maxAttempts >= 1) { "maxAttempts must be at least 1 or null" }
}

private fun validateAttempt(attempt: Int) {
    require(attempt >= 1) { "attempt must be at least 1" }
}
