package dev.stashy.worker.store.memory

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

    override suspend fun list(filter: JobHistory.Filter): List<JobHistory.Entry> = entries.value
        .asSequence()
        .filter { filter.jobId == null || it.jobId == filter.jobId }
        .filter { filter.handlerId == null || it.handlerId == filter.handlerId }
        .filter { filter.status == null || it.status == filter.status }
        .sortedBy { it.finishedAt }
        .take(filter.limit)
        .toList()

    /**
     * Removes entries that finished before [before].
     */
    public suspend fun clean(before: Instant): Unit {
        entries.update { current ->
            current.filter { it.finishedAt >= before }
        }
    }
}
