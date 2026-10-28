package dev.stashy.worker.job

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * In-memory [JobStore] suitable for standalone processes, tests, and single-worker applications.
 */
public class InMemoryJobStore(
    private val format: StringFormat,
) : JobStore {
    private data class Entry(
        val job: Job,
        val payload: String,
    )

    private val mutex = Mutex()
    private val jobs = linkedMapOf<Job.Id, Entry>()

    override suspend fun get(id: Job.Id): Job? = mutex.withLock { jobs[id]?.job }

    override suspend fun list(): List<Job> = mutex.withLock { jobs.values.map { it.job } }

    override suspend fun <C : Any> create(
        definition: Job.Definition,
        config: C,
        serializer: KSerializer<C>,
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
            payload = format.encodeToString(serializer, config),
        )
        true
    }

    override suspend fun <C : Any> updateDefinition(
        definition: Job.Definition,
        config: C,
        serializer: KSerializer<C>,
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
            payload = format.encodeToString(serializer, config),
        )
        true
    }

    override suspend fun <C : Any> readConfig(
        claim: JobStore.Claimed,
        serializer: KSerializer<C>,
    ): C = mutex.withLock {
        val entry = checkNotNull(jobs[claim.job.definition.id]) {
            "Job '${claim.job.definition.id.value}' no longer exists"
        }
        format.decodeFromString(serializer, entry.payload)
    }

    override suspend fun delete(id: Job.Id): Boolean = mutex.withLock {
        if (jobs[id]?.job?.status == Job.Status.RUNNING) return@withLock false
        jobs.remove(id) != null
    }

    override suspend fun claim(request: JobStore.ClaimRequest): JobStore.Claimed? = mutex.withLock {
        val horizon = request.now + request.prepareWindow
        val entry = jobs.values
            .asSequence()
            .filter { it.job.isRunnableAt(horizon, request.supportedHandlerIds) }
            .minByOrNull { it.job.nextRun!! }
            ?: return@withLock null

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

    override suspend fun renew(claim: JobStore.Claimed, now: Instant, lease: Duration): Boolean = mutex.withLock {
        val entry = jobs[claim.job.definition.id] ?: return@withLock false
        if (!entry.job.isOwnedBy(claim)) return@withLock false
        val lock = entry.job.lock ?: return@withLock false
        jobs[claim.job.definition.id] = entry.copy(job = entry.job.copy(lock = lock.copy(expiresAt = now + lease)))
        true
    }

    override suspend fun complete(
        claim: JobStore.Claimed,
        now: Instant,
        nextRunAt: Instant?,
        error: String?,
    ): Boolean = mutex.withLock {
        val entry = jobs[claim.job.definition.id] ?: return@withLock false
        if (!entry.job.isOwnedBy(claim)) return@withLock false
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
        true
    }

    override suspend fun reschedule(
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

    override suspend fun fail(claim: JobStore.Claimed, now: Instant, error: String?): Boolean = mutex.withLock {
        val entry = jobs[claim.job.definition.id] ?: return@withLock false
        if (!entry.job.isOwnedBy(claim)) return@withLock false
        jobs[claim.job.definition.id] = entry.copy(
            job = entry.job.copy(
                status = Job.Status.FAILED,
                nextRun = null,
                lock = null,
                lastFinishedAt = now,
                lastError = error,
            ),
        )
        true
    }

    override suspend fun expired(now: Instant, limit: Int): List<JobStore.Claimed> = mutex.withLock {
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
