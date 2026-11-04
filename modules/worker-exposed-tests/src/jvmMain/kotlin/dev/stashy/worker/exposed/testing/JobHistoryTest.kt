package dev.stashy.worker.exposed.testing

import dev.stashy.worker.Worker
import dev.stashy.worker.job.Job
import dev.stashy.worker.store.JobHistory
import dev.stashy.worker.store.JobQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Suppress("FunctionName")
abstract class JobHistoryTest {
    protected abstract suspend fun createStore(): StoreFixture

    @Test
    fun `records and lists entries`() = runTest {
        val history = createStore().history
        val entry = entry(message = "done")

        history.record(entry)

        assertEquals(listOf(entry), history.list())
    }

    @Test
    fun `lists entries newest first`() = runTest {
        val history = createStore().history
        val later = entry(attempt = 2, finishedAt = now + 2.seconds)
        val earlier = entry(attempt = 1, finishedAt = now + 1.seconds)

        history.record(later)
        history.record(earlier)

        assertEquals(listOf(later, earlier), history.list())
    }

    @Test
    fun `filters by job, handler, status, and limit`() = runTest {
        val history = createStore().history
        val first = entry(attempt = 1, status = JobHistory.Status.FAILED)
        val second = entry(attempt = 2)
        val other = entry(jobId = Job.Id("other"), handlerId = "other-handler")

        listOf(first, second, other).forEach { history.record(it) }

        assertEquals(listOf(second, first), history.list(jobId = jobId))
        assertEquals(listOf(other), history.list(handlerId = "other-handler"))
        assertEquals(listOf(first), history.list(status = JobHistory.Status.FAILED))
        assertEquals(listOf(second), history.list(limit = 1))
    }

    @Test
    fun `worker records attempts in the exposed history`() = runBlocking {
        val store = createStore()
        val queue = store.queue
        val history = store.history
        val executed = CompletableDeferred<String>()

        val worker = Worker(id = "exposed-history-worker") {
            jobs(queue, history) {
                job<String> { _, config -> handler { executed.complete(config) } }
            }
            config {
                shutdownGracePeriod = 1.seconds
                defaults {
                    pollInterval = 10.milliseconds
                    prepareWindow = 50.milliseconds
                    leaseDuration = 1.seconds
                    heartbeatInterval = 300.milliseconds
                }
            }
        }

        worker.schedule("job", "payload") { once(Clock.System.now()) }
        worker.start(this)
        assertEquals("payload", withTimeout(5.seconds) { executed.await() })
        worker.stop()

        val recorded = assertNotNull(history.list().singleOrNull())
        assertEquals(Job.Id("job"), recorded.jobId)
        assertEquals(JobHistory.Status.SUCCEEDED, recorded.status)
        assertTrue(history.list(jobId = Job.Id("job")).isNotEmpty())
    }

    protected fun entry(
        jobId: Job.Id = this.jobId,
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

    protected val now: Instant = Instant.parse("2026-10-27T12:00:00Z")
    protected val jobId: Job.Id = Job.Id("job")
}

class StoreFixture(
    val queue: JobQueue<String>,
    val history: JobHistory.Readable,
)
