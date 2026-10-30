package dev.stashy.worker.exposed

import dev.stashy.worker.Worker
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.JobHistory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ExposedJobHistoryTest {
    @Test
    fun `records and lists entries`() = runTest {
        val history = h2History()
        val entry = entry()

        history.record(entry)

        assertEquals(listOf(entry), history.list())
    }

    @Test
    fun `lists entries ordered by finished time`() = runTest {
        val history = h2History()
        val later = entry(attempt = 2, finishedAt = now + 2.seconds)
        val earlier = entry(attempt = 1, finishedAt = now + 1.seconds)

        history.record(later)
        history.record(earlier)

        assertEquals(listOf(earlier, later), history.list())
    }

    @Test
    fun `filters by job, handler, status, and limit`() = runTest {
        val history = h2History()
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
    fun `worker records attempts in the exposed history`() = runBlocking {
        val database = database()
        transaction(database) { SchemaUtils.create(WorkerJobsTable, WorkerJobHistoryTable) }
        val queue = ExposedJobQueue(database, TestJson)
        val history = ExposedJobHistory(database)
        val executed = CompletableDeferred<String>()

        val worker = Worker(id = "exposed-history-worker") {
            jobs(queue, history) {
                job<String> { _, config -> executed.complete(config) }
            }
            config {
                pollInterval = 10.milliseconds
                prepareWindow = 50.milliseconds
                leaseDuration = 1.seconds
                heartbeatInterval = 300.milliseconds
                shutdownGracePeriod = 1.seconds
            }
        }

        worker.schedule("job", "payload") { once(Clock.System.now()) }
        worker.start(this)
        assertEquals("payload", withTimeout(5.seconds) { executed.await() })
        worker.stop()

        val recorded = assertNotNull(history.list().singleOrNull())
        assertEquals(Job.Id("job"), recorded.jobId)
        assertEquals(JobHistory.Status.SUCCEEDED, recorded.status)
        assertTrue(history.list(JobHistory.Filter(jobId = Job.Id("job"))).isNotEmpty())
    }

    private fun h2History(): ExposedJobHistory {
        val database = database()
        transaction(database) { SchemaUtils.create(WorkerJobHistoryTable) }
        return ExposedJobHistory(database)
    }

    private fun database(): Database = Database.connect(
        "jdbc:h2:mem:worker_history_${Uuid.random()};DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver",
    )

    private fun entry(
        jobId: Job.Id = ExposedJobHistoryTest.jobId,
        handlerId: String = "handler",
        attempt: Int = 1,
        status: JobHistory.Status = JobHistory.Status.SUCCEEDED,
        finishedAt: Instant = now + attempt.seconds,
    ): JobHistory.Entry = JobHistory.Entry(
        jobId = jobId,
        handlerId = handlerId,
        attempt = attempt,
        scheduledAt = now,
        startedAt = now,
        finishedAt = finishedAt,
        status = status,
    )

    private companion object {
        val now: Instant = Instant.parse("2026-10-27T12:00:00Z")
        val jobId = Job.Id("job")
    }
}
