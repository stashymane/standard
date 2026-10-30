package dev.stashy.worker.job

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.updateAndGet
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * In-memory [JobQueue] that snapshots configs through [format].
 *
 * Terminal occurrences are forgotten: completing a one-shot job or failing a job removes it, so
 * memory stays bounded by the number of pending and running jobs. A recurring job keeps its single
 * pending row between occurrences. Because completed one-shot jobs are forgotten, syncing their
 * definition schedules them again. Bind a [JobHistory] to keep execution records.
 */
public class InMemoryJobQueue(
    private val format: StringFormat,
) : JobQueue {
    private data class Entry(
        val job: Job,
        val storedConfig: String,
    )

    private val jobs = atomic<Map<Job.Id, Entry>>(emptyMap())

    override suspend fun get(id: Job.Id): Job? = jobs.value[id]?.job

    override suspend fun list(filter: JobQueue.Filter): List<Job> = jobs.value.values
        .asSequence()
        .map(Entry::job)
        .filter { job -> filter.statuses == null || job.status in filter.statuses }
        .filter { job -> filter.handlerId == null || job.definition.handlerId == filter.handlerId }
        .sortedBy { it.definition.id.value }
        .take(filter.limit)
        .toList()

    override suspend fun <C : Any> create(
        definition: Job.Definition,
        config: C,
        serializer: KSerializer<C>,
        scheduledAt: Instant,
    ): Boolean {
        val entry = Entry(
            job = Job(
                definition = definition,
                status = Job.Status.PENDING,
                scheduledAt = scheduledAt,
                nextRun = scheduledAt,
                attempt = 0,
                lock = null,
            ),
            storedConfig = format.encodeToString(serializer, config),
        )
        val previous = jobs.getAndUpdate { current ->
            if (current.containsKey(definition.id)) {
                current
            } else {
                current + (definition.id to entry)
            }
        }
        return !previous.containsKey(definition.id)
    }

    override suspend fun <C : Any> updateDefinition(
        definition: Job.Definition,
        config: C,
        serializer: KSerializer<C>,
        nextRunAt: Instant?,
    ): Boolean {
        val storedConfig = format.encodeToString(serializer, config)
        val previous = jobs.getAndUpdate { current ->
            val entry = current[definition.id] ?: return@getAndUpdate current
            val existing = entry.job
            val reset = if (nextRunAt != null && existing.status != Job.Status.RUNNING) {
                existing.copy(
                    status = Job.Status.PENDING,
                    scheduledAt = nextRunAt,
                    nextRun = nextRunAt,
                    attempt = 0,
                    lock = null,
                )
            } else {
                existing
            }
            current + (definition.id to Entry(reset.copy(definition = definition), storedConfig))
        }
        return previous.containsKey(definition.id)
    }

    override suspend fun <C : Any> readConfig(
        claim: JobQueue.Claimed,
        serializer: KSerializer<C>,
    ): C {
        val entry = checkNotNull(jobs.value[claim.job.definition.id]) {
            "Job '${claim.job.definition.id.value}' no longer exists"
        }
        return format.decodeFromString(serializer, entry.storedConfig)
    }

    override suspend fun delete(id: Job.Id): Boolean {
        val previous = jobs.getAndUpdate { current ->
            val job = current[id]?.job
            if (job == null || job.status == Job.Status.RUNNING) {
                current
            } else {
                current - id
            }
        }
        val job = previous[id]?.job
        return job != null && job.status != Job.Status.RUNNING
    }

    override suspend fun claim(request: JobQueue.ClaimRequest, limit: Int): List<JobQueue.Claimed> {
        require(limit >= 1) { "limit must be at least 1" }
        val horizon = request.now + request.prepareWindow
        var claimed = emptyList<JobQueue.Claimed>()
        jobs.updateAndGet { current ->
            val runnable = current.values
                .filter { it.job.isRunnableAt(horizon, request.supportedHandlerIds) }
                .sortedBy { it.job.nextRun!! }
                .take(limit)
            if (runnable.isEmpty()) {
                claimed = emptyList()
                current
            } else {
                val updated = LinkedHashMap(current)
                claimed = runnable.map { entry ->
                    val candidate = entry.job
                    val attempt = candidate.attempt + 1
                    val claimedJob = candidate.copy(
                        status = Job.Status.RUNNING,
                        attempt = attempt,
                        lock = Job.Lock(
                            token = Uuid.random().toString(),
                            lockedBy = request.workerId,
                            expiresAt = request.now + request.lease,
                        ),
                    )
                    updated[candidate.definition.id] = entry.copy(job = claimedJob)
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

    override suspend fun renew(claim: JobQueue.Claimed, now: Instant, lease: Duration): Boolean {
        val previous = jobs.getAndUpdate { current ->
            val entry = current[claim.job.definition.id]
            if (entry == null || !entry.job.isOwnedBy(claim)) {
                current
            } else {
                val lock = checkNotNull(entry.job.lock) { "A running job must have a lock" }
                val updatedJob = entry.job.copy(lock = lock.copy(expiresAt = now + lease))
                current + (claim.job.definition.id to entry.copy(job = updatedJob))
            }
        }
        return previous[claim.job.definition.id]?.job?.isOwnedBy(claim) == true
    }

    override suspend fun complete(claim: JobQueue.Claimed, nextRunAt: Instant?): Boolean {
        val previous = jobs.getAndUpdate { current ->
            val entry = current[claim.job.definition.id]
            if (entry == null || !entry.job.isOwnedBy(claim)) {
                current
            } else if (nextRunAt == null) {
                current - claim.job.definition.id
            } else {
                val updatedJob = entry.job.copy(
                    status = Job.Status.PENDING,
                    scheduledAt = nextRunAt,
                    nextRun = nextRunAt,
                    attempt = 0,
                    lock = null,
                )
                current + (claim.job.definition.id to entry.copy(job = updatedJob))
            }
        }
        return previous[claim.job.definition.id]?.job?.isOwnedBy(claim) == true
    }

    override suspend fun reschedule(claim: JobQueue.Claimed, nextRunAt: Instant): Boolean {
        val previous = jobs.getAndUpdate { current ->
            val entry = current[claim.job.definition.id]
            if (entry == null || !entry.job.isOwnedBy(claim)) {
                current
            } else {
                val updatedJob = entry.job.copy(
                    status = Job.Status.PENDING,
                    nextRun = nextRunAt,
                    lock = null,
                )
                current + (claim.job.definition.id to entry.copy(job = updatedJob))
            }
        }
        return previous[claim.job.definition.id]?.job?.isOwnedBy(claim) == true
    }

    override suspend fun fail(claim: JobQueue.Claimed): Boolean {
        val previous = jobs.getAndUpdate { current ->
            val entry = current[claim.job.definition.id]
            if (entry == null || !entry.job.isOwnedBy(claim)) {
                current
            } else {
                current - claim.job.definition.id
            }
        }
        return previous[claim.job.definition.id]?.job?.isOwnedBy(claim) == true
    }

    override suspend fun expired(now: Instant, limit: Int): List<JobQueue.Claimed> = jobs.value.values
        .asSequence()
        .map(Entry::job)
        .filter { job ->
            job.status == Job.Status.RUNNING &&
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

    private fun Job.isRunnableAt(horizon: Instant, handlerIds: Set<String>): Boolean =
        status == Job.Status.PENDING &&
                definition.enabled &&
                definition.handlerId in handlerIds &&
                nextRun != null &&
                nextRun <= horizon

    private fun Job.isOwnedBy(claim: JobQueue.Claimed): Boolean =
        status == Job.Status.RUNNING && lock?.token == claim.lockToken
}
