package dev.stashy.worker.exposed

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Instant

/**
 * Persistence layout for worker execution history.
 *
 * Create or migrate this table through the application's existing schema management.
 */
public object WorkerJobHistoryTable : Table("worker_job_history") {
    public val id: Column<Long> = long("id").autoIncrement()
    public val jobId: Column<String> = varchar("job_id", 255)
    public val handlerId: Column<String> = varchar("handler_id", 255)
    public val attempt: Column<Int> = integer("attempt")
    public val scheduledAt: Column<Instant> = timestamp("scheduled_at")
    public val startedAt: Column<Instant> = timestamp("started_at")
    public val finishedAt: Column<Instant> = timestamp("finished_at")
    public val status: Column<String> = varchar("status", 16)
    public val error: Column<String?> = text("error").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(id)

    init {
        index("worker_job_history_job", false, jobId, finishedAt)
        index("worker_job_history_finished", false, finishedAt)
    }
}
