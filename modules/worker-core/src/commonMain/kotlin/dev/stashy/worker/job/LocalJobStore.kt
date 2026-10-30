package dev.stashy.worker.job

import kotlinx.serialization.KSerializer
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Node-local [JobStore] that keeps only live jobs and skips config serialization.
 *
 * Terminal jobs are forgotten: completing a one-shot job or failing a job removes it, so memory
 * stays bounded by the number of pending and running jobs. A recurring job keeps its single
 * pending row between occurrences. Because completed one-shot jobs are forgotten, syncing their
 * definition schedules them again.
 *
 * Configs are held by reference - do not mutate a config after handing it to the store.
 * Use [InMemoryJobStore] when terminal jobs must remain inspectable, and
 * a durable store when jobs must survive restarts or be shared between nodes.
 */
public class LocalJobStore : JobStore {
    private val engine = InMemoryJobStoreEngine(retainTerminalJobs = false)

    override suspend fun get(id: Job.Id): Job? = engine.get(id)

    override suspend fun list(filter: JobStore.Filter): List<Job> = engine.list(filter)

    override suspend fun <C : Any> create(
        definition: Job.Definition,
        config: C,
        serializer: KSerializer<C>,
        scheduledAt: Instant,
    ): Boolean = engine.create(definition, config, scheduledAt)

    override suspend fun <C : Any> updateDefinition(
        definition: Job.Definition,
        config: C,
        serializer: KSerializer<C>,
        nextRunAt: Instant?,
    ): Boolean = engine.updateDefinition(definition, config, nextRunAt)

    override suspend fun <C : Any> readConfig(
        claim: JobStore.Claimed,
        serializer: KSerializer<C>,
    ): C {
        @Suppress("UNCHECKED_CAST")
        return engine.readStoredConfig(claim) as C
    }

    override suspend fun delete(id: Job.Id): Boolean = engine.delete(id)

    override suspend fun claim(request: JobStore.ClaimRequest, limit: Int): List<JobStore.Claimed> =
        engine.claim(request, limit)

    override suspend fun renew(claim: JobStore.Claimed, now: Instant, lease: Duration): Boolean =
        engine.renew(claim, now, lease)

    override suspend fun complete(
        claim: JobStore.Claimed,
        now: Instant,
        nextRunAt: Instant?,
        error: String?,
    ): Boolean = engine.complete(claim, now, nextRunAt, error)

    override suspend fun reschedule(
        claim: JobStore.Claimed,
        now: Instant,
        nextRunAt: Instant,
        error: String?,
    ): Boolean = engine.reschedule(claim, now, nextRunAt, error)

    override suspend fun fail(claim: JobStore.Claimed, now: Instant, error: String?): Boolean =
        engine.fail(claim, now, error)

    override suspend fun expired(now: Instant, limit: Int): List<JobStore.Claimed> = engine.expired(now, limit)
}
