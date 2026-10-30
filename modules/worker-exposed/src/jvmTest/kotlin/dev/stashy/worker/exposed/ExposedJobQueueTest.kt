package dev.stashy.worker.exposed

import dev.stashy.worker.Worker
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.JobQueue
import dev.stashy.worker.job.LateExecutionPolicy
import dev.stashy.worker.job.QueuedJob
import dev.stashy.worker.job.RetryPolicy
import dev.stashy.worker.schedule.Schedule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ExposedJobQueueTest {
    @Test
    fun `creates and reads a job with its config`() = runTest {
        h2Queue().use { fixture ->
            val definition = definition(
                "job",
                schedule = Schedule.Cron("0 0 3 ? * * *", "UTC"),
                retry = RetryPolicy(maxAttempts = 3, initialDelay = 2.seconds),
                latePolicy = LateExecutionPolicy.FailAfter(5.seconds),
            )

            assertTrue(fixture.queue.create(definition, "value", serializer(), now))
            assertFalse(fixture.queue.create(definition, "other", serializer(), now))
            val job = assertNotNull(fixture.queue.get(definition.id))
            assertEquals(definition, job.definition)
            assertEquals(QueuedJob.Status.PENDING, job.status)
            assertEquals(now, job.scheduledAt)
            assertEquals(now, job.nextRun)
            assertEquals(0, job.attempt)

            val claim = assertNotNull(fixture.queue.claim(request(now)))
            assertEquals("value", fixture.queue.readConfig(claim, serializer()))
        }
    }

    @Test
    fun `claim filters by handler and prepare window`() = runTest {
        h2Queue().use { fixture ->
            val due = definition("due", handlerId = "due")
            val future = definition("future", handlerId = "future", schedule = Schedule.Once(now + 1.minutes))
            assertTrue(fixture.queue.create(due, Unit, serializer(), now))
            assertTrue(fixture.queue.create(future, Unit, serializer(), now + 1.minutes))

            assertNull(fixture.queue.claim(request(now, supported = setOf("other"))))
            assertNull(fixture.queue.claim(request(now, supported = setOf("future"))))
            val claim = assertNotNull(fixture.queue.claim(request(now, supported = setOf("due"))))
            assertEquals(due.id, claim.job.definition.id)
        }
    }

    @Test
    fun `claim takes the earliest due jobs up to the limit`() = runTest {
        h2Queue().use { fixture ->
            val first = definition("first")
            val second = definition("second", schedule = Schedule.Once(now + 1.seconds))
            val later = definition("later", schedule = Schedule.Once(now + 30.seconds))
            assertTrue(fixture.queue.create(later, Unit, serializer(), now + 30.seconds))
            assertTrue(fixture.queue.create(first, Unit, serializer(), now))
            assertTrue(fixture.queue.create(second, Unit, serializer(), now + 1.seconds))

            val claims = fixture.queue.claim(request(now), limit = 2)

            assertEquals(listOf("first", "second"), claims.map { it.job.definition.id.value })
            assertTrue(claims.all { it.job.status == QueuedJob.Status.RUNNING && it.attempt == 1 })
            assertEquals(QueuedJob.Status.PENDING, fixture.queue.get(later.id)?.status)
        }
    }

    @Test
    fun `list filters by status and handler and applies the limit`() = runTest {
        h2Queue().use { fixture ->
            assertTrue(fixture.queue.create(definition("one"), Unit, serializer(), now))
            assertTrue(
                fixture.queue.create(
                    definition("two", schedule = Schedule.Once(now + 1.seconds)),
                    Unit, serializer(), now + 1.seconds,
                ),
            )
            assertTrue(
                fixture.queue.create(
                    definition("other", handlerId = "other", schedule = Schedule.Once(now + 2.seconds)),
                    Unit, serializer(), now + 2.seconds,
                ),
            )
            assertNotNull(fixture.queue.claim(request(now)))

            assertEquals(
                listOf("one", "other", "two"),
                fixture.queue.list().map { it.definition.id.value },
            )
            assertEquals(
                listOf("one"),
                fixture.queue.list(JobQueue.Filter(statuses = setOf(QueuedJob.Status.RUNNING))).map { it.definition.id.value },
            )
            assertEquals(
                listOf("other"),
                fixture.queue.list(JobQueue.Filter(handlerId = "other")).map { it.definition.id.value },
            )
            assertEquals(2, fixture.queue.list(JobQueue.Filter(limit = 2)).size)
        }
    }

    @Test
    fun `only one concurrent claim succeeds`() = runTest {
        h2Queue().use { fixture ->
            val definition = definition("concurrent")
            assertTrue(fixture.queue.create(definition, Unit, serializer(), now))

            val claims = coroutineScope {
                List(10) {
                    async { fixture.queue.claim(request(now)) }
                }.awaitAll()
            }

            assertEquals(1, claims.count { it != null })
            val job = assertNotNull(fixture.queue.get(definition.id))
            assertEquals(QueuedJob.Status.RUNNING, job.status)
            assertEquals(1, job.attempt)
        }
    }

    @Test
    fun `lease transitions require the owning token`() = runTest {
        h2Queue().use { fixture ->
            val definition = definition("token")
            assertTrue(fixture.queue.create(definition, Unit, serializer(), now))
            val claim = assertNotNull(fixture.queue.claim(request(now, lease = 1.minutes)))
            val stale = claim.copy(
                job = claim.job.copy(lock = checkNotNull(claim.job.lock).copy(token = "stale")),
            )

            assertFalse(fixture.queue.renew(stale, now, 1.minutes))
            assertFalse(fixture.queue.complete(stale, null))
            assertFalse(fixture.queue.fail(stale))

            assertTrue(fixture.queue.renew(claim, now, 1.minutes))
            assertTrue(fixture.queue.complete(claim, null))
            assertNull(fixture.queue.get(definition.id))
        }
    }

    @Test
    fun `expired lease can be rescheduled`() = runTest {
        h2Queue().use { fixture ->
            val definition = definition("expired")
            assertTrue(fixture.queue.create(definition, Unit, serializer(), now))
            val claim = assertNotNull(fixture.queue.claim(request(now, lease = 10.seconds)))

            val expired = fixture.queue.expired(now + 11.seconds)
            assertEquals(listOf(claim.job.definition.id), expired.map { it.job.definition.id })
            assertTrue(expired.single().recovered)

            val retryAt = now + 30.seconds
            assertTrue(fixture.queue.reschedule(claim, retryAt))
            assertNull(fixture.queue.claim(request(now + 12.seconds, lease = 10.seconds)))
            assertNotNull(fixture.queue.claim(request(retryAt, lease = 10.seconds)))
        }
    }

    @Test
    fun `update preserves runtime state and resets a changed schedule`() = runTest {
        h2Queue().use { fixture ->
            val definition = definition("update")
            assertTrue(fixture.queue.create(definition, "before", serializer(), now))
            val claim = assertNotNull(fixture.queue.claim(request(now, lease = 1.minutes)))
            assertTrue(fixture.queue.reschedule(claim, now + 10.seconds))

            assertTrue(fixture.queue.update(definition, "after", serializer()))
            val preserved = assertNotNull(fixture.queue.get(definition.id))
            assertEquals(QueuedJob.Status.PENDING, preserved.status)
            assertEquals(1, preserved.attempt)
            assertEquals(now + 10.seconds, preserved.nextRun)
            assertEquals("after", fixture.queue.readConfig(claim, serializer()))

            val changed = definition.copy(schedule = Schedule.FixedRate(1.minutes))
            val nextRunAt = now + 1.minutes
            assertTrue(fixture.queue.update(changed, "changed", serializer(), nextRunAt))
            val updated = assertNotNull(fixture.queue.get(definition.id))
            assertEquals(QueuedJob.Status.PENDING, updated.status)
            assertEquals(0, updated.attempt)
            assertEquals(nextRunAt, updated.nextRun)
        }
    }

    @Test
    fun `delete rejects a running job`() = runTest {
        h2Queue().use { fixture ->
            val definition = definition("delete")
            assertTrue(fixture.queue.create(definition, Unit, serializer(), now))
            val claim = assertNotNull(fixture.queue.claim(request(now)))

            assertFalse(fixture.queue.delete(definition.id))
            assertTrue(fixture.queue.reschedule(claim, now + 1.seconds))
            assertTrue(fixture.queue.delete(definition.id))
            assertNull(fixture.queue.get(definition.id))
        }
    }

    @Test
    fun `worker executes a job through the exposed queue`() = runBlocking {
        h2Queue().use { fixture ->
            val executed = CompletableDeferred<String>()
            val worker = Worker(id = "exposed-worker") {
                jobs(fixture.queue) {
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
        }
    }

    @Test
    fun `sqlite supports the basic job lifecycle`() = runTest {
        sqliteQueue().use { fixture ->
            val definition = definition("sqlite")
            assertTrue(fixture.queue.create(definition, "value", serializer(), now))

            val claim = assertNotNull(fixture.queue.claim(request(now)))
            assertEquals("value", fixture.queue.readConfig(claim, serializer()))
            assertTrue(fixture.queue.complete(claim, null))
            assertNull(fixture.queue.get(definition.id))
        }
    }

    private fun h2Queue(): QueueFixture {
        val database = Database.connect(
            "jdbc:h2:mem:worker_${Uuid.random()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction(database) { SchemaUtils.create(WorkerJobsTable) }
        return QueueFixture(ExposedJobQueue(database, TestJson))
    }

    private fun sqliteQueue(): QueueFixture {
        val path = Files.createTempFile("worker-exposed-", ".sqlite")
        val database = Database.connect(
            "jdbc:sqlite:${path.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )
        transaction(database) { SchemaUtils.create(WorkerJobsTable) }
        return QueueFixture(ExposedJobQueue(database, TestJson), path)
    }

    private fun definition(
        id: String,
        handlerId: String = "handler",
        schedule: Schedule = Schedule.Once(now),
        retry: RetryPolicy = RetryPolicy(),
        latePolicy: LateExecutionPolicy = LateExecutionPolicy.Run,
    ): Job = Job(
        id = Job.Id(id),
        handlerId = handlerId,
        schedule = schedule,
        retry = retry,
        latePolicy = latePolicy,
    )

    private fun request(
        at: Instant,
        supported: Set<String> = setOf("handler"),
        lease: Duration = 1.minutes,
    ): JobQueue.ClaimRequest = JobQueue.ClaimRequest(
        now = at,
        supportedHandlerIds = supported,
        prepareWindow = 1.seconds,
        lease = lease,
        workerId = "worker",
    )

    private class QueueFixture(
        val queue: ExposedJobQueue,
        private val path: Path? = null,
    ) : AutoCloseable {
        override fun close() {
            path?.let(Files::deleteIfExists)
        }
    }

    private companion object {
        val now: Instant = Instant.parse("2026-10-27T12:00:00Z")
    }
}
