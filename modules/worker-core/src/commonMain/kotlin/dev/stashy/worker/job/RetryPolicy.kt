package dev.stashy.worker.job

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Controls retries after a job throws or its lease expires.
 *
 * [maxAttempts] includes the initial execution. A crash consumes an attempt as soon as the job is
 * claimed, so a job that repeatedly kills its worker eventually becomes [Job.Status.FAILED].
 */
@Serializable
public data class RetryPolicy(
    public val maxAttempts: Int = 5,
    public val initialDelay: Duration = 10.seconds,
    public val maxDelay: Duration = 10.minutes,
    public val multiplier: Double = 2.0,
    public val jitter: Double = 0.2,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
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

    public fun delayFor(attempt: Int): Duration {
        require(attempt >= 1) { "attempt must be at least 1" }
        val exponent = (attempt - 1).coerceAtMost(30)
        val delay = minOf(initialDelay * multiplier.pow(exponent.toDouble()), maxDelay)
        return if (jitter == 0.0) delay else delay * (1.0 + Random.nextDouble(-jitter, jitter))
    }
}
