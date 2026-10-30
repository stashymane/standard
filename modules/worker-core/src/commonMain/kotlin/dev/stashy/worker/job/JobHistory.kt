package dev.stashy.worker.job

import kotlin.time.Instant

/**
 * Execution history for jobs.
 *
 * History is optional: workers record finished attempts when a [JobHistory] is bound to their
 * `jobs` block. Recording happens after the queue transition succeeds, so the queue remains the
 * source of truth.
 */
public interface JobHistory {
    /**
     * Appends a finished attempt.
     */
    public suspend fun record(entry: Entry)

    /**
     * One finished execution attempt.
     */
    public data class Entry(
        public val jobId: Job.Id,
        public val handlerId: String,
        public val attempt: Int,
        public val scheduledAt: Instant,
        public val startedAt: Instant,
        public val finishedAt: Instant,
        public val status: Status,
        /**
         * Short status message, such as a failure reason. Null when there is nothing to report.
         */
        public val message: String? = null,
    )

    public enum class Status {
        SUCCEEDED,
        FAILED,
    }

    /**
     * Criteria for reading history. Null fields match everything.
     */
    public data class Filter(
        public val jobId: Job.Id? = null,
        public val handlerId: String? = null,
        public val status: Status? = null,
        public val limit: Int = Int.MAX_VALUE,
    ) {
        init {
            require(handlerId == null || handlerId.isNotBlank()) { "handlerId must be null or non-blank" }
            require(limit >= 1) { "limit must be at least 1" }
        }
    }

    /**
     * History that can be read back, ordered by [Entry.finishedAt].
     */
    public interface Readable : JobHistory {
        public suspend fun list(filter: Filter = Filter()): List<Entry>
    }
}
