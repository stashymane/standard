package dev.stashy.worker.job

import dev.stashy.worker.schedule.Schedule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * Decides what to do when an exact-instant ([Schedule.Once]) job starts later than scheduled.
 */
@Serializable
public sealed interface LateExecutionPolicy {
    @Serializable
    @SerialName("run")
    public data object Run : LateExecutionPolicy

    @Serializable
    @SerialName("fail")
    public data class FailAfter(public val tolerance: Duration) : LateExecutionPolicy {
        init {
            require(tolerance.isFinite() && tolerance >= Duration.ZERO) {
                "tolerance must be a non-negative finite duration"
            }
        }
    }
}
