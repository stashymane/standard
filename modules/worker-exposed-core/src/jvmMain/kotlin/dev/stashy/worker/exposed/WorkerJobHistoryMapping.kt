package dev.stashy.worker.exposed

import dev.stashy.worker.exposed.tables.WorkerJobHistoryTable
import dev.stashy.worker.job.Job
import dev.stashy.worker.store.JobHistory
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

@WorkerExposedInternalApi
public fun ResultRow.toEntry(): JobHistory.Entry = JobHistory.Entry(
    jobId = Job.Id(this[WorkerJobHistoryTable.jobId]),
    handlerId = this[WorkerJobHistoryTable.handlerId],
    attempt = this[WorkerJobHistoryTable.attempt],
    scheduledAt = this[WorkerJobHistoryTable.scheduledAt],
    startedAt = this[WorkerJobHistoryTable.startedAt],
    finishedAt = this[WorkerJobHistoryTable.finishedAt],
    status = this[WorkerJobHistoryTable.status],
    message = this[WorkerJobHistoryTable.message],
)

@WorkerExposedInternalApi
public fun <T : UpdateBuilder<*>> T.applyEntry(entry: JobHistory.Entry) {
    this[WorkerJobHistoryTable.jobId] = entry.jobId.value
    this[WorkerJobHistoryTable.handlerId] = entry.handlerId
    this[WorkerJobHistoryTable.attempt] = entry.attempt
    this[WorkerJobHistoryTable.scheduledAt] = entry.scheduledAt
    this[WorkerJobHistoryTable.startedAt] = entry.startedAt
    this[WorkerJobHistoryTable.finishedAt] = entry.finishedAt
    this[WorkerJobHistoryTable.status] = entry.status
    this[WorkerJobHistoryTable.message] = entry.message
}

@WorkerExposedInternalApi
public fun historyListConditions(
    jobId: Job.Id?,
    handlerId: String?,
    status: JobHistory.Status?,
): Op<Boolean> = buildList {
    jobId?.let { add(WorkerJobHistoryTable.jobId eq it.value) }
    handlerId?.let { add(WorkerJobHistoryTable.handlerId eq it) }
    status?.let { add(WorkerJobHistoryTable.status eq it) }
}.ifEmpty { listOf(Op.TRUE) }.compoundAnd()
