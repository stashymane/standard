package dev.stashy.worker.job

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Synchronized in-memory implementation of the [JobStore] lease protocol, shared by
 * [InMemoryJobStore] and [LocalJobStore].
 *
 * Configs are stored opaquely; encoding them is the caller's concern. [retainTerminalJobs]
 * decides whether terminal transitions write the final state or remove the job.
 */
internal class InMemoryJobStoreEngine(
    private val retainTerminalJobs: Boolean,
) {
    private data class Entry(
        val job: Job,
        val storedConfig: Any?,
    )

    private val mutex = Mutex()
    private val jobs = linkedMapOf<Job.Id, Entry>()

    suspend fun get(id: Job.Id): Job? = mutex.withLock { jobs[id]?.job }

    suspend fun list(filter: JobStore.Filter): List<Job> = mutex.withLock {
        jobs.values
            .asSequence()
            .map { it.job }
            .filter { job -> filter.statuses == null || job.status in filter.statuses }
            .filter { job -> filter.handlerId == null || job.definition.handlerId == filter.handlerId }
            .sortedBy { it.definition.id.value }
            .take(filter.limit)
            .toList()
    }

    suspend fun create(
        definition: Job.Definition,
        storedConfig: Any?,
        scheduledAt: Instant,
    ): Boolean = mutex.withLock {
        if (jobs.containsKey(definition.id)) return@withLock false
        jobs[definition.id] = Entry(
            job = Job(
                definition = definition,
                status = Job.Status.PENDING,
                scheduledAt = scheduledAt,
                nextRun = scheduledAt,
                attempt = 0,
                lock = null,
                lastStartedAt = null,
                lastFinishedAt = null,
                lastError = null,
            ),
            storedConfig = storedConfig,
        )
        true
    }

    suspend fun updateDefinition(
        definition: Job.Definition,
        storedConfig: Any?,
        nextRunAt: Instant?,
    ): Boolean = mutex.withLock {
        val entry = jobs[definition.id] ?: return@withLock false
        val existing = entry.job
        val reset = if (nextRunAt != null && existing.status != Job.Status.RUNNING) {
            existing.copy(
                status = Job.Status.PENDING,
                scheduledAt = nextRunAt,
                nextRun = nextRunAt,
                attempt = 0,
                lock = null,
                lastError = null,
            )
        } else {
            existing
        }
        jobs[definition.id] = Entry(
            job = reset.copy(definition = definition),
            storedConfig = storedConfig,
        )
        true
    }

    suspend fun readStoredConfig(claim: JobStore.Claimed): Any? = mutex.withLock {
        val entry = checkNotNull(jobs[claim.job.definition.id]) {
            "Job '${claim.job.definition.id.value}' no longer exists"
        }
        entry.storedConfig
    }

    suspend fun delete(id: Job.Id): Boolean = mutex.withLock {
        if (jobs[id]?.job?.status == Job.Status.RUNNING) return@withLock false
        jobs.remove(id) != null
    }

    suspend fun claim(request: JobStore.ClaimRequest, limit: Int): List<JobStore.Claimed> = mutex.withLock {
        require(limit >= 1) { "limit must be at least 1" }
        val horizon = request.now + request.prepareWindow
        val runnable = jobs.values
            .filter { it.job.isRunnableAt(horizon, request.supportedHandlerIds) }
            .sortedBy { it.job.nextRun!! }
            .take(limit)

        runnable.map { entry ->
            val candidate = entry.job
            val attempt = candidate.attempt + 1
            val claimed = candidate.copy(
                status = Job.Status.RUNNING,
                attempt = attempt,
                lock = Job.Lock(
                    token = Uuid.random().toString(),
                    lockedBy = request.workerId,
                    expiresAt = request.now + request.lease,
                ),
                lastStartedAt = request.now,
            )
            jobs[candidate.definition.id] = entry.copy(job = claimed)
            JobStore.Claimed(
                job = claimed,
                attempt = attempt,
                scheduledAt = candidate.scheduledAt ?: candidate.nextRun ?: request.now,
                recovered = false,
            )
        }
    }

    suspend fun renew(claim: JobStore.Claimed, now: Instant, lease: Duration): Boolean = mutex.withLock {
        val entry = jobs[claim.job.definition.id] ?: return@withLock false
        if (!entry.job.isOwnedBy(claim)) return@withLock false
        val lock = entry.job.lock ?: return@withLock false
        jobs[claim.job.definition.id] = entry.copy(job = entry.job.copy(lock = lock.copy(expiresAt = now + lease)))
        true
    }

    suspend fun complete(
        claim: JobStore.Claimed,
        now: Instant,
        nextRunAt: Instant?,
        error: String?,
    ): Boolean = mutex.withLock {
        val entry = jobs[claim.job.definition.id] ?: return@withLock false
        if (!entry.job.isOwnedBy(claim)) return@withLock false
        if (nextRunAt == null && !retainTerminalJobs) {
            jobs.remove(claim.job.definition.id)
        } else {
            jobs[claim.job.definition.id] = entry.copy(
                job = entry.job.copy(
                    status = if (nextRunAt == null) Job.Status.COMPLETED else Job.Status.PENDING,
                    scheduledAt = nextRunAt ?: entry.job.scheduledAt,
                    nextRun = nextRunAt,
                    attempt = 0,
                    lock = null,
                    lastFinishedAt = now,
                    lastError = error,
                ),
            )
        }
        true
    }

    suspend fun reschedule(
        claim: JobStore.Claimed,
        now: Instant,
        nextRunAt: Instant,
        error: String?,
    ): Boolean = mutex.withLock {
        val entry = jobs[claim.job.definition.id] ?: return@withLock false
        if (!entry.job.isOwnedBy(claim)) return@withLock false
        jobs[claim.job.definition.id] = entry.copy(
            job = entry.job.copy(
                status = Job.Status.PENDING,
                nextRun = nextRunAt,
                lock = null,
                lastFinishedAt = now,
                lastError = error,
            ),
        )
        true
    }

    suspend fun fail(claim: JobStore.Claimed, now: Instant, error: String?): Boolean = mutex.withLock {
        val entry = jobs[claim.job.definition.id] ?: return@withLock false
        if (!entry.job.isOwnedBy(claim)) return@withLock false
        if (retainTerminalJobs) {
            jobs[claim.job.definition.id] = entry.copy(
                job = entry.job.copy(
                    status = Job.Status.FAILED,
                    nextRun = null,
                    lock = null,
                    lastFinishedAt = now,
                    lastError = error,
                ),
            )
        } else {
            jobs.remove(claim.job.definition.id)
        }
        true
    }

    suspend fun expired(now: Instant, limit: Int): List<JobStore.Claimed> = mutex.withLock {
        jobs.values
            .asSequence()
            .map { it.job }
            .filter { job ->
                job.status == Job.Status.RUNNING &&
                        job.lock?.let { it.expiresAt <= now } == true
            }
            .take(limit)
            .mapNotNull { job ->
                if (job.lock == null) return@mapNotNull null
                JobStore.Claimed(
                    job = job,
                    attempt = job.attempt,
                    scheduledAt = job.scheduledAt ?: job.nextRun ?: now,
                    recovered = true,
                )
            }
            .toList()
    }

    private fun Job.isRunnableAt(horizon: Instant, handlerIds: Set<String>): Boolean =
        status == Job.Status.PENDING &&
                definition.enabled &&
                definition.handlerId in handlerIds &&
                nextRun != null &&
                nextRun <= horizon

    private fun Job.isOwnedBy(claim: JobStore.Claimed): Boolean =
        status == Job.Status.RUNNING && lock?.token == claim.lockToken
}
