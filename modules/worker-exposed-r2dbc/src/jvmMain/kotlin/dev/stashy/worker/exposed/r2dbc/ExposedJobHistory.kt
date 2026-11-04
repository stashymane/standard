package dev.stashy.worker.exposed.r2dbc

import dev.stashy.worker.exposed.WorkerExposedInternalApi
import dev.stashy.worker.exposed.applyEntry
import dev.stashy.worker.exposed.historyListConditions
import dev.stashy.worker.exposed.tables.WorkerJobHistoryTable
import dev.stashy.worker.exposed.toEntry
import dev.stashy.worker.job.Job
import dev.stashy.worker.store.JobHistory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.r2dbc.*

/**
 * [JobHistory.Readable] backed by an existing Exposed R2DBC [database].
 */
@OptIn(WorkerExposedInternalApi::class)
public class ExposedJobHistory(
    private val database: R2dbcDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JobHistory.Readable {
    /**
     * Creates [WorkerJobHistoryTable].
     */
    public suspend fun createSchema(): Unit = transaction {
        SchemaUtils.create(WorkerJobHistoryTable)
    }

    override suspend fun record(entry: JobHistory.Entry): Unit = transaction {
        WorkerJobHistoryTable.insert { it.applyEntry(entry) }
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
            .where { historyListConditions(jobId, handlerId, status) }
            .orderBy(WorkerJobHistoryTable.finishedAt to SortOrder.DESC)
            .let { query -> if (limit == null) query else query.limit(limit) }
            .map(ResultRow::toEntry)
            .toList()
    }

    private suspend fun <T> transaction(block: suspend R2dbcTransaction.() -> T): T =
        database.transaction(dispatcher, block)
}
