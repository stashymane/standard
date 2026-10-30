package dev.stashy.worker.job

import dev.stashy.worker.schedule.Schedule
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * The persisted description of a job.
 *
 * [handlerId] is the serial name of the handler's config type. The store owns the encoded
 * handler configuration and may persist this object as a whole or map it to columns.
 */
@Serializable
public data class Job(
    public val id: Id,
    public val handlerId: String,
    public val schedule: Schedule,
    public val enabled: Boolean = true,
    public val retry: RetryPolicy = RetryPolicy(),
    public val latePolicy: LateExecutionPolicy = LateExecutionPolicy.Run,
) {
    @Serializable
    @JvmInline
    public value class Id(public val value: String) {
        public companion object {
            public fun random(): Id = Id(Uuid.generateV7().toString())
        }
    }
}
