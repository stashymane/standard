package dev.stashy.worker.exposed

import dev.stashy.worker.job.Job
import dev.stashy.worker.job.LateExecutionPolicy
import dev.stashy.worker.job.QueuedJob
import dev.stashy.worker.job.RetryPolicy
import dev.stashy.worker.schedule.Schedule
import kotlinx.serialization.StringFormat
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import kotlin.time.Instant

internal fun ResultRow.toJob(format: StringFormat): QueuedJob {
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
            schedule = format.decodeSchedule(this[WorkerJobsTable.schedule]),
            enabled = this[WorkerJobsTable.enabled],
            retry = format.decodeRetry(this[WorkerJobsTable.retryPolicy]),
            latePolicy = format.decodeLatePolicy(this[WorkerJobsTable.latePolicy]),
        ),
        status = status,
        scheduledAt = this[WorkerJobsTable.scheduledAt],
        nextRun = this[WorkerJobsTable.nextRunAt],
        attempt = this[WorkerJobsTable.attempt],
        lock = lock,
    )
}

internal fun UpdateBuilder<*>.setJob(
    job: Job,
    payload: String,
    format: StringFormat,
) {
    this[WorkerJobsTable.handlerId] = job.handlerId
    this[WorkerJobsTable.payload] = payload
    this[WorkerJobsTable.schedule] = format.encodeSchedule(job.schedule)
    this[WorkerJobsTable.enabled] = job.enabled
    this[WorkerJobsTable.retryPolicy] = format.encodeRetry(job.retry)
    this[WorkerJobsTable.latePolicy] = format.encodeLatePolicy(job.latePolicy)
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

private fun StringFormat.encodeSchedule(schedule: Schedule): String =
    encodeToString(serializer<Schedule>(), schedule)

private fun StringFormat.decodeSchedule(value: String): Schedule =
    decodeFromString(serializer<Schedule>(), value)

private fun StringFormat.encodeRetry(retry: RetryPolicy): String =
    encodeToString(serializer<RetryPolicy>(), retry)

private fun StringFormat.decodeRetry(value: String): RetryPolicy =
    decodeFromString(serializer<RetryPolicy>(), value)

private fun StringFormat.encodeLatePolicy(policy: LateExecutionPolicy): String =
    encodeToString(serializer<LateExecutionPolicy>(), policy)

private fun StringFormat.decodeLatePolicy(value: String): LateExecutionPolicy =
    decodeFromString(serializer<LateExecutionPolicy>(), value)
