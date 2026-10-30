package dev.stashy.worker.job

import kotlinx.serialization.KSerializer
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Queue of pending and running jobs.
 *
 * Implementations used by more than one worker must make [claim] atomic. The contract is:
 *
 * - [claim] selects up to `limit` [QueuedJob.Status.PENDING] jobs whose handler id is in
 *   [ClaimRequest.supportedHandlerIds] and whose next run is within the prepare window, earliest
 *   due first.
 * - It atomically marks each job [QueuedJob.Status.RUNNING] with a fresh [QueuedJob.Lock],
 *   increments the attempt counter, and sets the lease expiry, skipping jobs claimed concurrently
 *   by others.
 * - [renew], [complete], [reschedule], and [fail] must only modify a job while the supplied token
 *   still owns the lease, and return false otherwise.
 * - [expired] returns recently expired leases without claiming them. Callers transition those jobs
 *   with [reschedule] or [fail], which also guards against another worker acting first.
 *
 * Terminal occurrences leave the queue: [complete] without a next run and [fail] remove the job.
 * Execution history is recorded separately through [JobHistory].
 *
 * Encoding and decoding handler configs is a queue concern. [create] and [update] accept
 * a typed config with its serializer, and [readConfig] returns the decoded value for a claimed job.
 */
public interface JobQueue {
    /**
     * Criteria for an atomic claim.
     */
    public data class ClaimRequest(
        public val now: Instant,
        public val supportedHandlerIds: Set<String>,
        public val prepareWindow: Duration,
        public val lease: Duration,
        public val workerId: String,
    )

    /**
     * An owned lease on a job.
     */
    public data class Claimed(
        public val job: QueuedJob,
        public val attempt: Int,
        public val scheduledAt: Instant,
        /**
         * True when the job was returned by [expired], meaning its previous lease lapsed.
         */
        public val recovered: Boolean,
    ) {
        init {
            require(job.lock != null) { "A claimed job must be locked" }
        }

        /**
         * Token of the job's current [QueuedJob.Lock].
         */
        public val lockToken: String
            get() = job.lock!!.token
    }

    /**
     * Criteria for listing jobs. Null fields match everything.
     */
    public data class Filter(
        public val statuses: Set<QueuedJob.Status>? = null,
        public val handlerId: String? = null,
        public val limit: Int = Int.MAX_VALUE,
    ) {
        init {
            require(statuses == null || statuses.isNotEmpty()) { "statuses must be null or non-empty" }
            require(handlerId == null || handlerId.isNotBlank()) { "handlerId must be null or non-blank" }
            require(limit >= 1) { "limit must be at least 1" }
        }
    }

    public suspend fun get(id: Job.Id): QueuedJob?

    /**
     * Lists jobs matching [filter], ordered by job id, at most [Filter.limit] entries.
     */
    public suspend fun list(filter: Filter = Filter()): List<QueuedJob>

    /**
     * Creates a pending job at [scheduledAt]. Returns false when the id already exists.
     *
     * The queue persists [config] using [serializer] and its own format.
     */
    public suspend fun <C : Any> create(
        job: Job,
        config: C,
        serializer: KSerializer<C>,
        scheduledAt: Instant,
    ): Boolean

    /**
     * Replaces [job] and the stored config while preserving runtime state.
     *
     * When [nextRunAt] is non-null and the job is not currently running, the job is reset to
     * [QueuedJob.Status.PENDING] at that instant with its attempt counter cleared. This is used
     * when a job's schedule changes.
     */
    public suspend fun <C : Any> update(
        job: Job,
        config: C,
        serializer: KSerializer<C>,
        nextRunAt: Instant? = null,
    ): Boolean

    /**
     * Decodes the config of a claimed job using [serializer].
     */
    public suspend fun <C : Any> readConfig(
        claim: Claimed,
        serializer: KSerializer<C>,
    ): C

    /**
     * Deletes a job that is not currently running.
     */
    public suspend fun delete(id: Job.Id): Boolean

    /**
     * Atomically claims the next runnable job.
     */
    public suspend fun claim(request: ClaimRequest): Claimed? = claim(request, limit = 1).firstOrNull()

    /**
     * Atomically claims up to [limit] runnable jobs, earliest due first. Jobs claimed concurrently
     * by another worker are skipped.
     */
    public suspend fun claim(request: ClaimRequest, limit: Int): List<Claimed>

    /**
     * Extends the lease. Returns false if the caller no longer owns it.
     */
    public suspend fun renew(claim: Claimed, now: Instant, lease: Duration): Boolean

    /**
     * Completes an occurrence and schedules [nextRunAt], or removes the job from the queue when
     * null.
     */
    public suspend fun complete(claim: Claimed, nextRunAt: Instant?): Boolean

    /**
     * Releases a runnable occurrence for a later attempt without resetting the attempt counter.
     */
    public suspend fun reschedule(claim: Claimed, nextRunAt: Instant): Boolean

    /**
     * Marks the job as permanently failed and removes it from the queue.
     */
    public suspend fun fail(claim: Claimed): Boolean

    /**
     * Returns jobs whose lease expired at or before [now].
     */
    public suspend fun expired(now: Instant, limit: Int = 64): List<Claimed>
}
