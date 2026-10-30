package dev.stashy.worker.job

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A job together with its persisted runtime state in a queue.
 */
@Serializable
public data class QueuedJob(
    public val definition: Job,
    public val status: Status,
    public val scheduledAt: Instant?,
    public val nextRun: Instant?,
    public val attempt: Int,
    public val lock: Lock? = null,
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
    public enum class Status {
        PENDING,

        /** The job is locked by a worker. */
        RUNNING,
    }
}
