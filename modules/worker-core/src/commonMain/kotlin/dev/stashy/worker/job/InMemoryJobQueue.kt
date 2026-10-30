package dev.stashy.worker.job

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.updateAndGet
import kotlinx.serialization.StringFormat
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * In-memory [JobQueue] storing payloads produced by [codec].
 *
 * Terminal occurrences are forgotten: completing a one-shot job or failing a job removes it, so
 * memory stays bounded by the number of pending and running jobs. A recurring job keeps its single
 * pending row between occurrences. Because completed one-shot jobs are forgotten, syncing their
 * definition schedules them again. Bind a [JobHistory] to keep execution records.
 */
public class InMemoryJobQueue<P : Any>(
    override val codec: ConfigCodec<P>,
) : JobQueue<P> {
    public companion object {
        /**
         * Creates a queue that stores configs as strings encoded with [format].
         */
        public operator fun invoke(format: StringFormat): InMemoryJobQueue<String> =
            InMemoryJobQueue(StringConfigCodec(format))
    }

    private val jobs = atomic<Map<Job.Id, QueuedJob<P>>>(emptyMap())

    override suspend fun get(id: Job.Id): QueuedJob<P>? = jobs.value[id]

    override suspend fun list(filter: JobQueue.Filter): List<QueuedJob<P>> = jobs.value.values
        .asSequence()
        .filter { job -> filter.statuses == null || job.status in filter.statuses }
        .filter { job -> filter.handlerId == null || job.definition.handlerId == filter.handlerId }
        .sortedBy { it.definition.id.value }
        .take(filter.limit)
        .toList()

    override suspend fun create(job: Job, payload: P, scheduledAt: Instant): Boolean {
        val entry = QueuedJob(
            definition = job,
            payload = payload,
            status = QueuedJob.Status.PENDING,
            scheduledAt = scheduledAt,
            nextRun = scheduledAt,
            attempt = 0,
            lock = null,
        )
        val previous = jobs.getAndUpdate { current ->
            if (current.containsKey(job.id)) {
                current
            } else {
                current + (job.id to entry)
            }
        }
        return !previous.containsKey(job.id)
    }

    override suspend fun update(job: Job, payload: P, nextRunAt: Instant?): Boolean {
        val previous = jobs.getAndUpdate { current ->
            val existing = current[job.id] ?: return@getAndUpdate current
            val reset = if (nextRunAt != null && existing.status != QueuedJob.Status.RUNNING) {
                existing.copy(
                    status = QueuedJob.Status.PENDING,
                    scheduledAt = nextRunAt,
                    nextRun = nextRunAt,
                    attempt = 0,
                    lock = null,
                )
            } else {
                existing
            }
            current + (job.id to reset.copy(definition = job, payload = payload))
        }
        return previous.containsKey(job.id)
    }

    override suspend fun delete(id: Job.Id): Boolean {
        val previous = jobs.getAndUpdate { current ->
            val job = current[id]
            if (job == null || job.status == QueuedJob.Status.RUNNING) {
                current
            } else {
                current - id
            }
        }
        val job = previous[id]
        return job != null && job.status != QueuedJob.Status.RUNNING
    }

    override suspend fun claim(request: JobQueue.ClaimRequest, limit: Int): List<JobQueue.Claimed<P>> {
        require(limit >= 1) { "limit must be at least 1" }
        val horizon = request.now + request.prepareWindow
        var claimed = emptyList<JobQueue.Claimed<P>>()
        jobs.updateAndGet { current ->
            val runnable = current.values
                .filter { it.isRunnableAt(horizon, request.supportedHandlerIds) }
                .sortedBy { it.nextRun!! }
                .take(limit)
            if (runnable.isEmpty()) {
                claimed = emptyList()
                current
            } else {
                val updated = LinkedHashMap(current)
                claimed = runnable.map { candidate ->
                    val attempt = candidate.attempt + 1
                    val claimedJob = candidate.copy(
                        status = QueuedJob.Status.RUNNING,
                        attempt = attempt,
                        lock = QueuedJob.Lock(
                            token = Uuid.random().toString(),
                            lockedBy = request.workerId,
                            expiresAt = request.now + request.lease,
                        ),
                    )
                    updated[candidate.definition.id] = claimedJob
                    JobQueue.Claimed(
                        job = claimedJob,
                        attempt = attempt,
                        scheduledAt = candidate.scheduledAt ?: candidate.nextRun ?: request.now,
                        recovered = false,
                    )
                }
                updated
            }
        }
        return claimed
    }

    override suspend fun renew(claim: JobQueue.Claimed<P>, now: Instant, lease: Duration): Boolean {
        val previous = jobs.getAndUpdate { current ->
            val job = current[claim.job.definition.id]
            if (job == null || !job.isOwnedBy(claim)) {
                current
            } else {
                val lock = checkNotNull(job.lock) { "A running job must have a lock" }
                current + (claim.job.definition.id to job.copy(lock = lock.copy(expiresAt = now + lease)))
            }
        }
        return previous[claim.job.definition.id]?.isOwnedBy(claim) == true
    }

    override suspend fun complete(claim: JobQueue.Claimed<P>, nextRunAt: Instant?): Boolean {
        val previous = jobs.getAndUpdate { current ->
            val job = current[claim.job.definition.id]
            if (job == null || !job.isOwnedBy(claim)) {
                current
            } else if (nextRunAt == null) {
                current - claim.job.definition.id
            } else {
                current + (claim.job.definition.id to job.copy(
                    status = QueuedJob.Status.PENDING,
                    scheduledAt = nextRunAt,
                    nextRun = nextRunAt,
                    attempt = 0,
                    lock = null,
                ))
            }
        }
        return previous[claim.job.definition.id]?.isOwnedBy(claim) == true
    }

    override suspend fun reschedule(claim: JobQueue.Claimed<P>, nextRunAt: Instant): Boolean {
        val previous = jobs.getAndUpdate { current ->
            val job = current[claim.job.definition.id]
            if (job == null || !job.isOwnedBy(claim)) {
                current
            } else {
                current + (claim.job.definition.id to job.copy(
                    status = QueuedJob.Status.PENDING,
                    nextRun = nextRunAt,
                    lock = null,
                ))
            }
        }
        return previous[claim.job.definition.id]?.isOwnedBy(claim) == true
    }

    override suspend fun fail(claim: JobQueue.Claimed<P>): Boolean {
        val previous = jobs.getAndUpdate { current ->
            val job = current[claim.job.definition.id]
            if (job == null || !job.isOwnedBy(claim)) {
                current
            } else {
                current - claim.job.definition.id
            }
        }
        return previous[claim.job.definition.id]?.isOwnedBy(claim) == true
    }

    override suspend fun expired(now: Instant, limit: Int): List<JobQueue.Claimed<P>> = jobs.value.values
        .asSequence()
        .filter { job ->
            job.status == QueuedJob.Status.RUNNING &&
                    job.lock?.let { it.expiresAt <= now } == true
        }
        .take(limit)
        .mapNotNull { job ->
            if (job.lock == null) return@mapNotNull null
            JobQueue.Claimed(
                job = job,
                attempt = job.attempt,
                scheduledAt = job.scheduledAt ?: job.nextRun ?: now,
                recovered = true,
            )
        }
        .toList()

    private fun QueuedJob<P>.isRunnableAt(horizon: Instant, handlerIds: Set<String>): Boolean =
        status == QueuedJob.Status.PENDING &&
                definition.enabled &&
                definition.handlerId in handlerIds &&
                nextRun != null &&
                nextRun <= horizon

    private fun QueuedJob<P>.isOwnedBy(claim: JobQueue.Claimed<P>): Boolean =
        status == QueuedJob.Status.RUNNING && lock?.token == claim.lockToken
}
