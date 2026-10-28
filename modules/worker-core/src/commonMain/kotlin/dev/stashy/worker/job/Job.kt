package dev.stashy.worker.job

import dev.stashy.worker.schedule.Schedule
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A job definition together with its persisted runtime state.
 */
@Serializable
public data class Job(
    public val definition: Definition,
    public val status: Status,
    public val scheduledAt: Instant?,
    public val nextRun: Instant?,
    public val attempt: Int,
    public val lock: Lock? = null,
    public val lastStartedAt: Instant? = null,
    public val lastFinishedAt: Instant? = null,
    public val lastError: String? = null,
) {
    init {
        require((status == Status.RUNNING) == (lock != null)) {
            "A running job must have a lock, and a non-running job must not"
        }
    }

    /**
     * An exclusive lease held by a worker.
     *
     * The token, owner, and expiration only exist together, so a partial lock cannot be represented.
     */
    @Serializable
    public data class Lock(
        public val token: String,
        public val lockedBy: String,
        public val expiresAt: Instant,
    ) {
        init {
            require(token.isNotBlank()) { "lock token must not be blank" }
            require(lockedBy.isNotBlank()) { "lock owner must not be blank" }
        }
    }

    @Serializable
    @JvmInline
    public value class Id(public val value: String) {
        public companion object {
            public fun random(): Id = Id(Uuid.generateV7().toString())
        }
    }

    /**
     * The persisted description of a job.
     *
     * [handlerId] is the serial name of the handler's config type. The store owns the encoded
     * handler configuration and may persist this object as a whole or map it to columns.
     */
    @Serializable
    public data class Definition(
        public val id: Id,
        public val handlerId: String,
        public val schedule: Schedule,
        public val enabled: Boolean = true,
        public val retry: RetryPolicy = RetryPolicy(),
        public val latePolicy: LateExecutionPolicy = LateExecutionPolicy.Run,
    )

    @Serializable
    public enum class Status {
        PENDING,

        /** The job is locked by a worker. */
        RUNNING,
        COMPLETED,
        FAILED,
    }
}
