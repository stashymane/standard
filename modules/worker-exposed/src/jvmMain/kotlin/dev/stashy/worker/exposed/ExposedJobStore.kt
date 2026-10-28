package dev.stashy.worker.exposed

import dev.stashy.worker.job.Job
import dev.stashy.worker.job.JobStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [JobStore] backed by an existing Exposed JDBC [database].
 *
 * The store owns no connections and does not create or migrate [WorkerJobsTable].
 */
public class ExposedJobStore(
    private val database: Database,
    private val format: StringFormat,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JobStore {
    /**
     * Creates [WorkerJobsTable] when the application does not manage schema elsewhere.
     */
    public suspend fun createSchema(): Unit = transaction {
        SchemaUtils.create(WorkerJobsTable)
    }

    override suspend fun get(id: Job.Id): Job? = transaction {
        WorkerJobsTable.selectAll()
            .where { WorkerJobsTable.id eq id.value }
            .singleOrNull()
            ?.toJob(format)
    }

    override suspend fun list(): List<Job> = transaction {
        WorkerJobsTable.selectAll().map { it.toJob(format) }
    }

    override suspend fun <C : Any> create(
        definition: Job.Definition,
        config: C,
        serializer: KSerializer<C>,
        scheduledAt: Instant,
    ): Boolean {
        val payload = format.encodeToString(serializer, config)
        return try {
            transaction {
                if (exists(definition.id)) return@transaction false
                WorkerJobsTable.insert {
                    it[WorkerJobsTable.id] = definition.id.value
                    it.setDefinition(definition, payload, format)
                    it[WorkerJobsTable.status] = Job.Status.PENDING.name
                    it[WorkerJobsTable.scheduledAt] = scheduledAt
                    it[WorkerJobsTable.nextRunAt] = scheduledAt
                    it[WorkerJobsTable.attempt] = 0
                    it[WorkerJobsTable.lockedBy] = null
                    it[WorkerJobsTable.lockToken] = null
                    it[WorkerJobsTable.leaseExpiresAt] = null
                    it[WorkerJobsTable.lastStartedAt] = null
                    it[WorkerJobsTable.lastFinishedAt] = null
                    it[WorkerJobsTable.lastError] = null
                }
                true
            }
        } catch (error: ExposedSQLException) {
            if (transaction { exists(definition.id) }) false else throw error
        }
    }

    override suspend fun <C : Any> updateDefinition(
        definition: Job.Definition,
        config: C,
        serializer: KSerializer<C>,
        nextRunAt: Instant?,
    ): Boolean {
        val payload = format.encodeToString(serializer, config)
        return transaction {
            if (nextRunAt != null) {
                val reset = WorkerJobsTable.update({
                    (WorkerJobsTable.id eq definition.id.value) and
                            (WorkerJobsTable.status neq Job.Status.RUNNING.name)
                }) {
                    it.setDefinitionReset(definition, payload, nextRunAt, format)
                }
                if (reset > 0) return@transaction true
            }

            WorkerJobsTable.update({ WorkerJobsTable.id eq definition.id.value }) {
                it.setDefinition(definition, payload, format)
            } > 0
        }
    }

    override suspend fun <C : Any> readConfig(
        claim: JobStore.Claimed,
        serializer: KSerializer<C>,
    ): C = transaction {
        val payload = WorkerJobsTable.select(WorkerJobsTable.payload)
            .where { WorkerJobsTable.id eq claim.job.definition.id.value }
            .singleOrNull()
            ?.get(WorkerJobsTable.payload)
            ?: error("Job '${claim.job.definition.id.value}' no longer exists")
        format.decodeFromString(serializer, payload)
    }

    override suspend fun delete(id: Job.Id): Boolean = transaction {
        WorkerJobsTable.deleteWhere {
            (WorkerJobsTable.id eq id.value) and (WorkerJobsTable.status neq Job.Status.RUNNING.name)
        } > 0
    }

    override suspend fun claim(request: JobStore.ClaimRequest): JobStore.Claimed? = transaction {
        val horizon = request.now + request.prepareWindow
        val attempted = mutableSetOf<String>()

        while (true) {
            val candidate = WorkerJobsTable.selectAll()
                .where {
                    val due = (WorkerJobsTable.status eq Job.Status.PENDING.name) and
                            (WorkerJobsTable.enabled eq true) and
                            (WorkerJobsTable.handlerId inList request.supportedHandlerIds) and
                            (WorkerJobsTable.nextRunAt lessEq horizon)
                    if (attempted.isEmpty()) {
                        due
                    } else {
                        due and (WorkerJobsTable.id notInList attempted)
                    }
                }
                .orderBy(WorkerJobsTable.nextRunAt to SortOrder.ASC)
                .limit(1)
                .firstOrNull()
                ?.toJob(format)
                ?: return@transaction null

            attempted += candidate.definition.id.value
            if (attempted.size > MAX_CLAIM_ATTEMPTS) return@transaction null
            val token = Uuid.random().toString()
            val leaseUntil = request.now + request.lease
            val updated = WorkerJobsTable.update({
                (WorkerJobsTable.id eq candidate.definition.id.value) and
                        (WorkerJobsTable.status eq Job.Status.PENDING.name)
            }) {
                it.setClaim(request.workerId, token, leaseUntil, request.now)
            }

            if (updated == 1) {
                val claimed = candidate.copy(
                    status = Job.Status.RUNNING,
                    attempt = candidate.attempt + 1,
                    lock = Job.Lock(token, request.workerId, leaseUntil),
                    lastStartedAt = request.now,
                )
                return@transaction JobStore.Claimed(
                    job = claimed,
                    attempt = claimed.attempt,
                    scheduledAt = candidate.scheduledAt ?: candidate.nextRun ?: request.now,
                    recovered = false,
                )
            }
        }
        null
    }

    override suspend fun renew(claim: JobStore.Claimed, now: Instant, lease: Duration): Boolean = transaction {
        WorkerJobsTable.update({
            (WorkerJobsTable.id eq claim.job.definition.id.value) and
                    (WorkerJobsTable.status eq Job.Status.RUNNING.name) and
                    (WorkerJobsTable.lockToken eq claim.lockToken)
        }) {
            it.setLease(now + lease)
        } == 1
    }

    override suspend fun complete(
        claim: JobStore.Claimed,
        now: Instant,
        nextRunAt: Instant?,
        error: String?,
    ): Boolean = transaction {
        WorkerJobsTable.update({
            (WorkerJobsTable.id eq claim.job.definition.id.value) and
                    (WorkerJobsTable.status eq Job.Status.RUNNING.name) and
                    (WorkerJobsTable.lockToken eq claim.lockToken)
        }) {
            it.setCompletion(nextRunAt, now, error)
        } == 1
    }

    override suspend fun reschedule(
        claim: JobStore.Claimed,
        now: Instant,
        nextRunAt: Instant,
        error: String?,
    ): Boolean = transaction {
        WorkerJobsTable.update({
            (WorkerJobsTable.id eq claim.job.definition.id.value) and
                    (WorkerJobsTable.status eq Job.Status.RUNNING.name) and
                    (WorkerJobsTable.lockToken eq claim.lockToken)
        }) {
            it.setReschedule(nextRunAt, now, error)
        } == 1
    }

    override suspend fun fail(claim: JobStore.Claimed, now: Instant, error: String?): Boolean = transaction {
        WorkerJobsTable.update({
            (WorkerJobsTable.id eq claim.job.definition.id.value) and
                    (WorkerJobsTable.status eq Job.Status.RUNNING.name) and
                    (WorkerJobsTable.lockToken eq claim.lockToken)
        }) {
            it.setFailure(now, error)
        } == 1
    }

    override suspend fun expired(now: Instant, limit: Int): List<JobStore.Claimed> = transaction {
        WorkerJobsTable.selectAll()
            .where {
                (WorkerJobsTable.status eq Job.Status.RUNNING.name) and
                        (WorkerJobsTable.leaseExpiresAt lessEq now)
            }
            .orderBy(WorkerJobsTable.leaseExpiresAt to SortOrder.ASC)
            .limit(limit)
            .map { row ->
                val job = row.toJob(format)
                JobStore.Claimed(
                    job = job,
                    attempt = job.attempt,
                    scheduledAt = job.scheduledAt ?: job.nextRun ?: now,
                    recovered = true,
                )
            }
    }

    private suspend fun <T> transaction(block: suspend JdbcTransaction.() -> T): T =
        withContext(dispatcher) {
            suspendTransaction(db = database) { block() }
        }

    private fun JdbcTransaction.exists(id: Job.Id): Boolean =
        WorkerJobsTable.select(WorkerJobsTable.id)
            .where { WorkerJobsTable.id eq id.value }
            .any()

    private companion object {
        const val MAX_CLAIM_ATTEMPTS: Int = 16
    }
}
