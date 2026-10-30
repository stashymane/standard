package dev.stashy.worker.exposed

import dev.stashy.worker.job.Job
import dev.stashy.worker.store.JobHistory
import org.jetbrains.exposed.v1.core.ResultRow

internal fun ResultRow.toEntry(): JobHistory.Entry = JobHistory.Entry(
    jobId = Job.Id(this[WorkerJobHistoryTable.jobId]),
    handlerId = this[WorkerJobHistoryTable.handlerId],
    attempt = this[WorkerJobHistoryTable.attempt],
    scheduledAt = this[WorkerJobHistoryTable.scheduledAt],
    startedAt = this[WorkerJobHistoryTable.startedAt],
    finishedAt = this[WorkerJobHistoryTable.finishedAt],
    status = this[WorkerJobHistoryTable.status],
    message = this[WorkerJobHistoryTable.message],
)
