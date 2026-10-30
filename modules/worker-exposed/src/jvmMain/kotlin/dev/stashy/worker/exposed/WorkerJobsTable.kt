package dev.stashy.worker.exposed

import dev.stashy.worker.job.QueuedJob
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Instant

/**
 * Persistence layout for queued worker jobs.
 *
 * Create or migrate this table through the application's existing schema management.
 */
public object WorkerJobsTable : Table("worker_jobs") {
    public val id: Column<String> = varchar("id", 255)
    public val handlerId: Column<String> = varchar("handler_id", 255)
    public val payload: Column<String> = text("payload")
    public val schedule: Column<String> = text("schedule")
    public val enabled: Column<Boolean> = bool("enabled")
    public val status: Column<QueuedJob.Status> = enumerationByName("status", 16)
    public val scheduledAt: Column<Instant> = timestamp("scheduled_at")
    public val nextRunAt: Column<Instant?> = timestamp("next_run_at").nullable()
    public val attempt: Column<Int> = integer("attempt")
    public val retryPolicy: Column<String> = text("retry_policy")
    public val latePolicy: Column<String> = text("late_policy")
    public val lockedBy: Column<String?> = varchar("locked_by", 255).nullable()
    public val lockToken: Column<String?> = varchar("lock_token", 64).nullable()
    public val leaseExpiresAt: Column<Instant?> = timestamp("lease_expires_at").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(id)

    init {
        index("worker_jobs_due", false, status, nextRunAt, handlerId)
        index("worker_jobs_leases", false, status, leaseExpiresAt)
    }
}
