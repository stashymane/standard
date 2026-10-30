package dev.stashy.worker.job

import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class InMemoryJobHistoryTest {
    @Test
    fun `records and lists entries`() = runTest {
        val history = InMemoryJobHistory()
        val entry = entry(attempt = 1, message = "done")

        history.record(entry)

        assertEquals(listOf(entry), history.list())
    }

    @Test
    fun `lists entries ordered by finished time`() = runTest {
        val history = InMemoryJobHistory()
        val later = entry(attempt = 2, finishedAt = now + 2.seconds)
        val earlier = entry(attempt = 1, finishedAt = now + 1.seconds)

        history.record(later)
        history.record(earlier)

        assertEquals(listOf(earlier, later), history.list())
    }

    @Test
    fun `filters by job, handler, status, and limit`() = runTest {
        val history = InMemoryJobHistory()
        val first = entry(attempt = 1, status = JobHistory.Status.FAILED)
        val second = entry(attempt = 2)
        val other = entry(jobId = Job.Id("other"), handlerId = "other-handler")

        listOf(first, second, other).forEach { history.record(it) }

        assertEquals(listOf(first, second), history.list(JobHistory.Filter(jobId = jobId)))
        assertEquals(listOf(other), history.list(JobHistory.Filter(handlerId = "other-handler")))
        assertEquals(listOf(first), history.list(JobHistory.Filter(status = JobHistory.Status.FAILED)))
        assertEquals(listOf(first), history.list(JobHistory.Filter(limit = 1)))
    }

    @Test
    fun `clean removes entries finished before the instant`() = runTest {
        val history = InMemoryJobHistory()
        val earlier = entry(attempt = 1, finishedAt = now + 1.seconds)
        val boundary = entry(attempt = 2, finishedAt = now + 2.seconds)
        val later = entry(attempt = 3, finishedAt = now + 3.seconds)

        listOf(earlier, boundary, later).forEach { history.record(it) }

        history.clean(now + 2.seconds)

        assertEquals(listOf(boundary, later), history.list())
    }

    private fun entry(
        jobId: Job.Id = InMemoryJobHistoryTest.jobId,
        handlerId: String = "handler",
        attempt: Int = 1,
        status: JobHistory.Status = JobHistory.Status.SUCCEEDED,
        finishedAt: Instant = now + attempt.seconds,
        message: String? = null,
    ): JobHistory.Entry = JobHistory.Entry(
        jobId = jobId,
        handlerId = handlerId,
        attempt = attempt,
        scheduledAt = now,
        startedAt = now,
        finishedAt = finishedAt,
        status = status,
        message = message,
    )

    private companion object {
        val now: Instant = Instant.parse("2026-10-27T12:00:00Z")
        val jobId = Job.Id("job")
    }
}
