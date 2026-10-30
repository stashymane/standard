package dev.stashy.worker.exposed

import dev.stashy.worker.job.Job
import dev.stashy.worker.job.JobQueue
import dev.stashy.worker.job.QueuedJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.StringFormat
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [JobQueue] backed by an existing Exposed JDBC [database].
 *
 * The queue owns no connections and does not create or migrate [WorkerJobsTable].
 *
 * On PostgreSQL, MySQL 8+/MariaDB 10.6+, and Oracle, claim candidates are selected with
 * `FOR UPDATE SKIP LOCKED` so concurrent workers claim disjoint jobs. Other dialects fall back to
 * optimistic updates that skip rows claimed concurrently.
 */
public class ExposedJobQueue(
    private val database: Database,
    private val format: StringFormat,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JobQueue {
    private val claimLockOption: ForUpdateOption? = when (database.dialect) {
        is PostgreSQLDialect -> ForUpdateOption.PostgreSQL.ForUpdate(ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED)
        is MysqlDialect -> ForUpdateOption.MySQL.ForUpdate(ForUpdateOption.MySQL.MODE.SKIP_LOCKED)
        is OracleDialect -> ForUpdateOption.Oracle.ForUpdateSkipLocked
        else -> null
    }

    /**
     * Creates [WorkerJobsTable] when the application does not manage schema elsewhere.
     */
    public suspend fun createSchema(): Unit = transaction {
        SchemaUtils.create(WorkerJobsTable)
    }

    override suspend fun get(id: Job.Id): QueuedJob? = transaction {
        WorkerJobsTable.selectAll()
            .where { WorkerJobsTable.id eq id.value }
            .singleOrNull()
            ?.toJob(format)
    }

    override suspend fun list(filter: JobQueue.Filter): List<QueuedJob> = transaction {
        val statuses = filter.statuses
        val handlerId = filter.handlerId
        WorkerJobsTable.selectAll()
            .where {
                var condition: Op<Boolean> = Op.TRUE
                if (statuses != null) {
                    condition = condition and (WorkerJobsTable.status inList statuses)
                }
                if (handlerId != null) {
                    condition = condition and (WorkerJobsTable.handlerId eq handlerId)
                }
                condition
            }
            .orderBy(WorkerJobsTable.id to SortOrder.ASC)
            .limit(filter.limit)
            .map { it.toJob(format) }
    }

    override suspend fun <C : Any> create(
        job: Job,
        config: C,
        serializer: KSerializer<C>,
        scheduledAt: Instant,
    ): Boolean {
        val payload = format.encodeToString(serializer, config)
        return try {
            transaction {
                if (exists(job.id)) return@transaction false
                WorkerJobsTable.insert {
                    it[WorkerJobsTable.id] = job.id.value
                    it.setJob(job, payload, format)
                    it[WorkerJobsTable.status] = QueuedJob.Status.PENDING
                    it[WorkerJobsTable.scheduledAt] = scheduledAt
                    it[WorkerJobsTable.nextRunAt] = scheduledAt
                    it[WorkerJobsTable.attempt] = 0
                    it[WorkerJobsTable.lockedBy] = null
                    it[WorkerJobsTable.lockToken] = null
                    it[WorkerJobsTable.leaseExpiresAt] = null
                }
                true
            }
        } catch (error: ExposedSQLException) {
            if (transaction { exists(job.id) }) false else throw error
        }
    }

    override suspend fun <C : Any> update(
        job: Job,
        config: C,
        serializer: KSerializer<C>,
        nextRunAt: Instant?,
    ): Boolean {
        val payload = format.encodeToString(serializer, config)
        return transaction {
            if (nextRunAt != null) {
                val reset = WorkerJobsTable.update({
                    (WorkerJobsTable.id eq job.id.value) and
                            (WorkerJobsTable.status neq QueuedJob.Status.RUNNING)
                }) {
                    it.setJobReset(job, payload, nextRunAt, format)
                }
                if (reset > 0) return@transaction true
            }

            WorkerJobsTable.update({ WorkerJobsTable.id eq job.id.value }) {
                it.setJob(job, payload, format)
            } > 0
        }
    }

    override suspend fun <C : Any> readConfig(
        claim: JobQueue.Claimed,
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
            (WorkerJobsTable.id eq id.value) and (WorkerJobsTable.status neq QueuedJob.Status.RUNNING)
        } > 0
    }

    override suspend fun claim(request: JobQueue.ClaimRequest, limit: Int): List<JobQueue.Claimed> = transaction {
        require(limit >= 1) { "limit must be at least 1" }
        val horizon = request.now + request.prepareWindow
        val candidateIds = WorkerJobsTable.select(WorkerJobsTable.id)
            .where {
                (WorkerJobsTable.status eq QueuedJob.Status.PENDING) and
                        (WorkerJobsTable.enabled eq true) and
                        (WorkerJobsTable.handlerId inList request.supportedHandlerIds) and
                        (WorkerJobsTable.nextRunAt lessEq horizon)
            }
            .orderBy(WorkerJobsTable.nextRunAt to SortOrder.ASC)
            .limit(limit)
            .let { query -> claimLockOption?.let(query::forUpdate) ?: query }
            .map { it[WorkerJobsTable.id] }

        candidateIds.mapNotNull { id ->
            val token = Uuid.random().toString()
            val leaseUntil = request.now + request.lease

            val updated = WorkerJobsTable.update({
                (WorkerJobsTable.id eq id) and (WorkerJobsTable.status eq QueuedJob.Status.PENDING)
            }) {
                it.setClaim(request.workerId, token, leaseUntil)
            }
            if (updated != 1) return@mapNotNull null

            val job = WorkerJobsTable.selectAll()
                .where { WorkerJobsTable.id eq id }
                .single()
                .toJob(format)
            JobQueue.Claimed(
                job = job,
                attempt = job.attempt,
                scheduledAt = job.scheduledAt ?: job.nextRun ?: request.now,
                recovered = false,
            )
        }
    }

    override suspend fun renew(claim: JobQueue.Claimed, now: Instant, lease: Duration): Boolean = transaction {
        WorkerJobsTable.update({ ownedBy(claim) }) {
            it.setLease(now + lease)
        } == 1
    }

    override suspend fun complete(claim: JobQueue.Claimed, nextRunAt: Instant?): Boolean = transaction {
        if (nextRunAt == null) {
            deleteOwned(claim)
        } else {
            WorkerJobsTable.update({ ownedBy(claim) }) {
                it.setCompletion(nextRunAt)
            } == 1
        }
    }

    override suspend fun reschedule(claim: JobQueue.Claimed, nextRunAt: Instant): Boolean = transaction {
        WorkerJobsTable.update({ ownedBy(claim) }) {
            it.setReschedule(nextRunAt)
        } == 1
    }

    override suspend fun fail(claim: JobQueue.Claimed): Boolean = transaction {
        deleteOwned(claim)
    }

    override suspend fun expired(now: Instant, limit: Int): List<JobQueue.Claimed> = transaction {
        WorkerJobsTable.selectAll()
            .where {
                (WorkerJobsTable.status eq QueuedJob.Status.RUNNING) and
                        (WorkerJobsTable.leaseExpiresAt lessEq now)
            }
            .orderBy(WorkerJobsTable.leaseExpiresAt to SortOrder.ASC)
            .limit(limit)
            .map { row ->
                val job = row.toJob(format)
                JobQueue.Claimed(
                    job = job,
                    attempt = job.attempt,
                    scheduledAt = job.scheduledAt ?: job.nextRun ?: now,
                    recovered = true,
                )
            }
    }

    private fun ownedBy(claim: JobQueue.Claimed): Op<Boolean> =
        (WorkerJobsTable.id eq claim.job.definition.id.value) and
                (WorkerJobsTable.status eq QueuedJob.Status.RUNNING) and
                (WorkerJobsTable.lockToken eq claim.lockToken)

    private fun JdbcTransaction.deleteOwned(claim: JobQueue.Claimed): Boolean =
        WorkerJobsTable.deleteWhere {
            (WorkerJobsTable.id eq claim.job.definition.id.value) and
                    (WorkerJobsTable.status eq QueuedJob.Status.RUNNING) and
                    (WorkerJobsTable.lockToken eq claim.lockToken)
        } > 0

    private suspend fun <T> transaction(block: suspend JdbcTransaction.() -> T): T =
        withContext(dispatcher) {
            suspendTransaction(db = database) { block() }
        }

    private fun JdbcTransaction.exists(id: Job.Id): Boolean =
        WorkerJobsTable.select(WorkerJobsTable.id)
            .where { WorkerJobsTable.id eq id.value }
            .any()
}
