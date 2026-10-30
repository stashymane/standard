package dev.stashy.worker.exposed

import dev.stashy.worker.job.JobHistory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/**
 * [JobHistory.Readable] backed by an existing Exposed JDBC [database].
 *
 * The history owns no connections and does not create or migrate [WorkerJobHistoryTable].
 */
public class ExposedJobHistory(
    private val database: Database,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JobHistory.Readable {
    /**
     * Creates [WorkerJobHistoryTable] when the application does not manage schema elsewhere.
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

    override suspend fun list(filter: JobHistory.Filter): List<JobHistory.Entry> = transaction {
        WorkerJobHistoryTable.selectAll()
            .where {
                var condition: Op<Boolean> = Op.TRUE
                filter.jobId?.let { condition = condition and (WorkerJobHistoryTable.jobId eq it.value) }
                filter.handlerId?.let { condition = condition and (WorkerJobHistoryTable.handlerId eq it) }
                filter.status?.let { condition = condition and (WorkerJobHistoryTable.status eq it) }
                condition
            }
            .orderBy(WorkerJobHistoryTable.finishedAt to SortOrder.ASC)
            .limit(filter.limit)
            .map { it.toEntry() }
    }

    private suspend fun <T> transaction(block: suspend JdbcTransaction.() -> T): T =
        withContext(dispatcher) {
            suspendTransaction(db = database) { block() }
        }
}
