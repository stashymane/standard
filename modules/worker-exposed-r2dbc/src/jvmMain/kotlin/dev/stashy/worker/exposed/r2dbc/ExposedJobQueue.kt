package dev.stashy.worker.exposed.r2dbc

import dev.stashy.worker.exposed.WorkerExposedInternalApi
import dev.stashy.worker.exposed.setClaim
import dev.stashy.worker.exposed.setCompletion
import dev.stashy.worker.exposed.setJob
import dev.stashy.worker.exposed.setJobReset
import dev.stashy.worker.exposed.setLease
import dev.stashy.worker.exposed.setReschedule
import dev.stashy.worker.exposed.tables.WorkerJobsTable
import dev.stashy.worker.exposed.toJob
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.QueuedJob
import dev.stashy.worker.store.ConfigCodec
import dev.stashy.worker.store.JobQueue
import dev.stashy.worker.store.StringConfigCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.StringFormat
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.*
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * [JobQueue] backed by an existing Exposed R2DBC [database].
 *
 * The queue owns no connections and does not create or migrate [dev.stashy.worker.exposed.tables.WorkerJobsTable].
 *
 * On PostgreSQL, MySQL 8+/MariaDB 10.6+, and Oracle, claim candidates are selected with
 * `FOR UPDATE SKIP LOCKED` so concurrent workers claim disjoint jobs. Other dialects fall back to
 * optimistic updates that skip rows claimed concurrently.
 */
@OptIn(WorkerExposedInternalApi::class)
public class ExposedJobQueue(
    private val database: R2dbcDatabase,
    private val format: StringFormat,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JobQueue<String> {
    override val codec: ConfigCodec<String> = StringConfigCodec(format)

    private val claimLockOption: ForUpdateOption? = when (database.dialect) {
        is PostgreSQLDialect -> ForUpdateOption.PostgreSQL.ForUpdate(ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED)
        is MysqlDialect -> ForUpdateOption.MySQL.ForUpdate(ForUpdateOption.MySQL.MODE.SKIP_LOCKED)
        is OracleDialect -> ForUpdateOption.Oracle.ForUpdateSkipLocked
        else -> null
    }

    /**
     * Creates [dev.stashy.worker.exposed.tables.WorkerJobsTable] when the application does not manage schema elsewhere.
     */
    public suspend fun createSchema(): Unit = transaction {
        SchemaUtils.create(WorkerJobsTable)
    }

    override suspend fun get(id: Job.Id): QueuedJob<String>? = transaction {
        WorkerJobsTable.selectAll()
            .where { WorkerJobsTable.id eq id.value }
            .singleOrNull()
            ?.toJob(format)
    }

    override suspend fun list(
        statuses: Set<QueuedJob.Status>?,
        handlerId: String?,
        limit: Int?,
    ): List<QueuedJob<String>> = transaction {
        require(statuses == null || statuses.isNotEmpty()) { "statuses must be null or non-empty" }
        require(handlerId == null || handlerId.isNotBlank()) { "handlerId must be null or non-blank" }
        require(limit == null || limit >= 1) { "limit must be at least 1" }
        WorkerJobsTable.selectAll()
            .where {
                buildList {
                    statuses?.let { add(WorkerJobsTable.status inList it) }
                    handlerId?.let { add(WorkerJobsTable.handlerId eq it) }
                }.ifEmpty { listOf(Op.TRUE) }.compoundAnd()
            }
            .orderBy(WorkerJobsTable.scheduledAt to SortOrder.DESC)
            .let { query -> if (limit == null) query else query.limit(limit) }
            .map { it.toJob(format) }
            .toList()
    }

    override suspend fun create(
        job: Job,
        payload: String,
        scheduledAt: Instant,
    ): Boolean {
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
        } catch (error: ExposedR2dbcException) {
            if (transaction { exists(job.id) }) false else throw error
        }
    }

    override suspend fun update(
        job: Job,
        payload: String,
        nextRunAt: Instant?,
    ): Boolean {
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

    override suspend fun delete(id: Job.Id): Boolean = transaction {
        WorkerJobsTable.deleteWhere {
            (WorkerJobsTable.id eq id.value) and (WorkerJobsTable.status neq QueuedJob.Status.RUNNING)
        } > 0
    }

    override suspend fun claim(request: JobQueue.ClaimRequest, limit: Int): List<JobQueue.Claimed<String>> =
        transaction {
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
                .toList()

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

    override suspend fun renew(
        claim: JobQueue.Claimed<String>,
        now: Instant,
        lease: Duration,
    ): JobQueue.Renewal = transaction {
        val renewed = WorkerJobsTable.update({
            ownedBy(claim) and (WorkerJobsTable.cancelRequested eq false)
        }) {
            it.setLease(now + lease)
        } == 1
        if (renewed) return@transaction JobQueue.Renewal.RENEWED

        val job = WorkerJobsTable.select(WorkerJobsTable.cancelRequested)
            .where { ownedBy(claim) }
            .singleOrNull()
            ?: return@transaction JobQueue.Renewal.LOST
        if (job[WorkerJobsTable.cancelRequested]) {
            JobQueue.Renewal.CANCELLATION_REQUESTED
        } else {
            JobQueue.Renewal.LOST
        }
    }

    override suspend fun requestCancellation(id: Job.Id): JobQueue.CancellationResult = transaction {
        val removed = WorkerJobsTable.deleteWhere {
            (WorkerJobsTable.id eq id.value) and (WorkerJobsTable.status eq QueuedJob.Status.PENDING)
        } > 0
        if (removed) return@transaction JobQueue.CancellationResult.REMOVED

        val flagged = WorkerJobsTable.update({
            (WorkerJobsTable.id eq id.value) and (WorkerJobsTable.status eq QueuedJob.Status.RUNNING)
        }) {
            it[WorkerJobsTable.cancelRequested] = true
        } == 1
        if (flagged) JobQueue.CancellationResult.FLAGGED else JobQueue.CancellationResult.NOT_FOUND
    }

    override suspend fun complete(claim: JobQueue.Claimed<String>, nextRunAt: Instant?): Boolean = transaction {
        if (nextRunAt == null) {
            deleteOwned(claim)
        } else {
            WorkerJobsTable.update({ ownedBy(claim) }) {
                it.setCompletion(nextRunAt)
            } == 1
        }
    }

    override suspend fun reschedule(claim: JobQueue.Claimed<String>, nextRunAt: Instant): Boolean = transaction {
        WorkerJobsTable.update({ ownedBy(claim) }) {
            it.setReschedule(nextRunAt)
        } == 1
    }

    override suspend fun fail(claim: JobQueue.Claimed<String>): Boolean = transaction {
        deleteOwned(claim)
    }

    override suspend fun expired(now: Instant, limit: Int): List<JobQueue.Claimed<String>> = transaction {
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
            .toList()
    }

    private fun ownedBy(claim: JobQueue.Claimed<String>): Op<Boolean> =
        (WorkerJobsTable.id eq claim.job.definition.id.value) and
                (WorkerJobsTable.status eq QueuedJob.Status.RUNNING) and
                (WorkerJobsTable.lockToken eq claim.lockToken)

    private suspend fun deleteOwned(claim: JobQueue.Claimed<String>): Boolean =
        WorkerJobsTable.deleteWhere {
            (WorkerJobsTable.id eq claim.job.definition.id.value) and
                    (WorkerJobsTable.status eq QueuedJob.Status.RUNNING) and
                    (WorkerJobsTable.lockToken eq claim.lockToken)
        } > 0

    private suspend fun <T> transaction(block: suspend R2dbcTransaction.() -> T): T =
        database.transaction(dispatcher, block)

    private suspend fun exists(id: Job.Id): Boolean =
        !WorkerJobsTable.select(WorkerJobsTable.id)
            .where { WorkerJobsTable.id eq id.value }
            .empty()
}
