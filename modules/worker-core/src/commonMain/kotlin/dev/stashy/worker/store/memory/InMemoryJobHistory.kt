package dev.stashy.worker.store.memory

import dev.stashy.worker.job.Job
import dev.stashy.worker.store.JobHistory
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlin.time.Instant

/**
 * In-memory [JobHistory] that keeps entries in insertion order.
 */
public class InMemoryJobHistory : JobHistory.Readable {
    private val entries = atomic<List<JobHistory.Entry>>(emptyList())

    override suspend fun record(entry: JobHistory.Entry) {
        entries.update { current -> current + entry }
    }

    override suspend fun list(
        jobId: Job.Id?,
        handlerId: String?,
        status: JobHistory.Status?,
        limit: Int?,
    ): List<JobHistory.Entry> {
        require(handlerId == null || handlerId.isNotBlank()) { "handlerId must be null or non-blank" }
        require(limit == null || limit >= 1) { "limit must be at least 1" }
        return entries.value
            .asSequence()
            .filter { jobId == null || it.jobId == jobId }
            .filter { handlerId == null || it.handlerId == handlerId }
            .filter { status == null || it.status == status }
            .sortedByDescending { it.finishedAt }
            .take(limit ?: Int.MAX_VALUE)
            .toList()
    }

    /**
     * Removes entries that finished before [before].
     */
    public suspend fun clean(before: Instant): Unit {
        entries.update { current ->
            current.filter { it.finishedAt >= before }
        }
    }
}
