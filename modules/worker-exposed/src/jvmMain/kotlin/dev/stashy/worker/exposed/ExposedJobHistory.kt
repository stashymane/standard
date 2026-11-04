package dev.stashy.worker.exposed

import dev.stashy.worker.exposed.tables.WorkerJobHistoryTable
import dev.stashy.worker.job.Job
import dev.stashy.worker.store.JobHistory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

/**
 * [JobHistory.Readable] backed by an existing Exposed JDBC [database].
 */
public class ExposedJobHistory(
    private val database: Database,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JobHistory.Readable {
    /**
     * Creates [WorkerJobHistoryTable].
     */
    public suspend fun createSchema(): Unit = transaction {
        SchemaUtils.create(WorkerJobHistoryTable)
    }

    override suspend fun record(entry: JobHistory.Entry): Unit = transaction {
        WorkerJobHistoryTable.insert {
            it[jobId] = entry.jobId.value
            it[handlerId] = entry.handlerId
            it[attempt] = entry.attempt
            it[scheduledAt] = entry.scheduledAt
            it[startedAt] = entry.startedAt
            it[finishedAt] = entry.finishedAt
            it[status] = entry.status
            it[message] = entry.message
        }
    }

    override suspend fun list(
        jobId: Job.Id?,
        handlerId: String?,
        status: JobHistory.Status?,
        limit: Int?,
    ): List<JobHistory.Entry> = transaction {
        require(handlerId == null || handlerId.isNotBlank()) { "handlerId must be null or non-blank" }
        require(limit == null || limit >= 1) { "limit must be at least 1" }
        WorkerJobHistoryTable.selectAll()
            .where {
                buildList {
                    jobId?.let { add(WorkerJobHistoryTable.jobId eq it.value) }
                    handlerId?.let { add(WorkerJobHistoryTable.handlerId eq it) }
                    status?.let { add(WorkerJobHistoryTable.status eq it) }
                }.ifEmpty { listOf(Op.TRUE) }.compoundAnd()
            }
            .orderBy(WorkerJobHistoryTable.finishedAt to SortOrder.DESC)
            .let { query -> if (limit == null) query else query.limit(limit) }
            .map(ResultRow::toEntry)
    }

    private suspend fun <T> transaction(block: suspend JdbcTransaction.() -> T): T =
        database.transaction(dispatcher, block)
}
