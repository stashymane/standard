package dev.stashy.worker.store

import dev.stashy.worker.job.Job
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
        CANCELLED,
    }

    /**
     * History that can be read back.
     */
    public interface Readable : JobHistory {
        /**
         * Lists entries matching the given criteria.
         * @return a list of history entries, sorted by newest to oldest
         */
        public suspend fun list(
            jobId: Job.Id? = null,
            handlerId: String? = null,
            status: Status? = null,
            limit: Int? = null,
        ): List<Entry>
    }
}
