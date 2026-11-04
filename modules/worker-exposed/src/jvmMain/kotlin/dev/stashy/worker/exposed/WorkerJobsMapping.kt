package dev.stashy.worker.exposed

import dev.stashy.worker.exposed.tables.WorkerJobsTable
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.QueuedJob
import kotlinx.serialization.StringFormat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import kotlin.time.Instant

internal fun ResultRow.toJob(format: StringFormat): QueuedJob<String> {
    val status = this[WorkerJobsTable.status]
    val lock = if (status == QueuedJob.Status.RUNNING) {
        QueuedJob.Lock(
            token = this[WorkerJobsTable.lockToken] ?: error("Running job is missing its lock token"),
            lockedBy = this[WorkerJobsTable.lockedBy] ?: error("Running job is missing its lock owner"),
            expiresAt = this[WorkerJobsTable.leaseExpiresAt] ?: error("Running job is missing its lease expiry"),
        )
    } else null

    return QueuedJob(
        definition = Job(
            id = Job.Id(this[WorkerJobsTable.id]),
            handlerId = this[WorkerJobsTable.handlerId],
            schedule = format.decodeFromString(this[WorkerJobsTable.schedule]),
            enabled = this[WorkerJobsTable.enabled],
            retry = format.decodeFromString(this[WorkerJobsTable.retryPolicy]),
            latePolicy = format.decodeFromString(this[WorkerJobsTable.latePolicy]),
        ),
        payload = this[WorkerJobsTable.payload],
        status = status,
        scheduledAt = this[WorkerJobsTable.scheduledAt],
        nextRun = this[WorkerJobsTable.nextRunAt],
        attempt = this[WorkerJobsTable.attempt],
        lock = lock,
        cancelRequested = this[WorkerJobsTable.cancelRequested],
    )
}

internal fun UpdateBuilder<*>.setJob(
    job: Job,
    payload: String,
    format: StringFormat,
) {
    this[WorkerJobsTable.handlerId] = job.handlerId
    this[WorkerJobsTable.payload] = payload
    this[WorkerJobsTable.schedule] = format.encodeToString(job.schedule)
    this[WorkerJobsTable.enabled] = job.enabled
    this[WorkerJobsTable.retryPolicy] = format.encodeToString(job.retry)
    this[WorkerJobsTable.latePolicy] = format.encodeToString(job.latePolicy)
}

internal fun UpdateBuilder<*>.setJobReset(
    job: Job,
    payload: String,
    nextRunAt: Instant,
    format: StringFormat,
) {
    setJob(job, payload, format)
    this[WorkerJobsTable.status] = QueuedJob.Status.PENDING
    this[WorkerJobsTable.scheduledAt] = nextRunAt
    this[WorkerJobsTable.nextRunAt] = nextRunAt
    this[WorkerJobsTable.attempt] = 0
    clearLock()
}

internal fun UpdateBuilder<*>.setClaim(
    workerId: String,
    token: String,
    leaseUntil: Instant,
) {
    this[WorkerJobsTable.status] = QueuedJob.Status.RUNNING
    this[WorkerJobsTable.lockedBy] = workerId
    this[WorkerJobsTable.lockToken] = token
    this[WorkerJobsTable.leaseExpiresAt] = leaseUntil
    this[WorkerJobsTable.attempt] = WorkerJobsTable.attempt + 1
}

internal fun UpdateBuilder<*>.setLease(leaseUntil: Instant) {
    this[WorkerJobsTable.leaseExpiresAt] = leaseUntil
}

internal fun UpdateBuilder<*>.setCompletion(nextRunAt: Instant) {
    this[WorkerJobsTable.status] = QueuedJob.Status.PENDING
    this[WorkerJobsTable.scheduledAt] = nextRunAt
    this[WorkerJobsTable.nextRunAt] = nextRunAt
    this[WorkerJobsTable.attempt] = 0
    clearLock()
}

internal fun UpdateBuilder<*>.setReschedule(nextRunAt: Instant) {
    this[WorkerJobsTable.status] = QueuedJob.Status.PENDING
    this[WorkerJobsTable.nextRunAt] = nextRunAt
    clearLock()
}

private fun UpdateBuilder<*>.clearLock() {
    this[WorkerJobsTable.lockedBy] = null
    this[WorkerJobsTable.lockToken] = null
    this[WorkerJobsTable.leaseExpiresAt] = null
}
