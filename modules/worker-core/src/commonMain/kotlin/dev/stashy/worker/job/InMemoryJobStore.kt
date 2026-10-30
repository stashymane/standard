package dev.stashy.worker.job

import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * In-memory [JobStore] that retains completed and failed jobs for inspection and snapshots configs
 * through [format].
 *
 * Suitable for tests and for simulating the full store contract in a single process. For
 * node-local jobs in long-running processes, prefer using [LocalJobStore], which removes terminal jobs and
 * skips serialization.
 */
public class InMemoryJobStore(
    private val format: StringFormat,
) : JobStore {
    private val engine = InMemoryJobStoreEngine(retainTerminalJobs = true)

    override suspend fun get(id: Job.Id): Job? = engine.get(id)

    override suspend fun list(filter: JobStore.Filter): List<Job> = engine.list(filter)

    override suspend fun <C : Any> create(
        definition: Job.Definition,
        config: C,
        serializer: KSerializer<C>,
        scheduledAt: Instant,
    ): Boolean = engine.create(definition, format.encodeToString(serializer, config), scheduledAt)

    override suspend fun <C : Any> updateDefinition(
        definition: Job.Definition,
        config: C,
        serializer: KSerializer<C>,
        nextRunAt: Instant?,
    ): Boolean = engine.updateDefinition(definition, format.encodeToString(serializer, config), nextRunAt)

    override suspend fun <C : Any> readConfig(
        claim: JobStore.Claimed,
        serializer: KSerializer<C>,
    ): C = format.decodeFromString(serializer, engine.readStoredConfig(claim) as String)

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
