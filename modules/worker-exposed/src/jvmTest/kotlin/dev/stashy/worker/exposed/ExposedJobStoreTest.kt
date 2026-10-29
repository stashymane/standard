package dev.stashy.worker.exposed

import dev.stashy.worker.Worker
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.JobStore
import dev.stashy.worker.job.LateExecutionPolicy
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

class ExposedJobStoreTest {
    @Test
    fun `creates and reads a job with its config`() = runTest {
        h2Store().use { fixture ->
            val definition = definition(
                "job",
                schedule = Schedule.Cron("0 0 3 ? * * *", "UTC"),
                retry = RetryPolicy(maxAttempts = 3, initialDelay = 2.seconds),
                latePolicy = LateExecutionPolicy.FailAfter(5.seconds),
            )

            assertTrue(fixture.store.create(definition, "value", serializer(), now))
            assertFalse(fixture.store.create(definition, "other", serializer(), now))
            val job = assertNotNull(fixture.store.get(definition.id))
            assertEquals(definition, job.definition)
            assertEquals(Job.Status.PENDING, job.status)
            assertEquals(now, job.scheduledAt)
            assertEquals(now, job.nextRun)
            assertEquals(0, job.attempt)

            val claim = assertNotNull(fixture.store.claim(request(now)))
            assertEquals("value", fixture.store.readConfig(claim, serializer()))
        }
    }

    @Test
    fun `claim filters by handler and prepare window`() = runTest {
        h2Store().use { fixture ->
            val due = definition("due", handlerId = "due")
            val future = definition("future", handlerId = "future", schedule = Schedule.Once(now + 1.minutes))
            assertTrue(fixture.store.create(due, Unit, serializer(), now))
            assertTrue(fixture.store.create(future, Unit, serializer(), now + 1.minutes))

            assertNull(fixture.store.claim(request(now, supported = setOf("other"))))
            assertNull(fixture.store.claim(request(now, supported = setOf("future"))))
            val claim = assertNotNull(fixture.store.claim(request(now, supported = setOf("due"))))
            assertEquals(due.id, claim.job.definition.id)
        }
    }

    @Test
    fun `claim takes the earliest due jobs up to the limit`() = runTest {
        h2Store().use { fixture ->
            val first = definition("first")
            val second = definition("second", schedule = Schedule.Once(now + 1.seconds))
            val later = definition("later", schedule = Schedule.Once(now + 30.seconds))
            assertTrue(fixture.store.create(later, Unit, serializer(), now + 30.seconds))
            assertTrue(fixture.store.create(first, Unit, serializer(), now))
            assertTrue(fixture.store.create(second, Unit, serializer(), now + 1.seconds))

            val claims = fixture.store.claim(request(now), limit = 2)

            assertEquals(listOf("first", "second"), claims.map { it.job.definition.id.value })
            assertTrue(claims.all { it.job.status == Job.Status.RUNNING && it.attempt == 1 })
            assertEquals(Job.Status.PENDING, fixture.store.get(later.id)?.status)
        }
    }

    @Test
    fun `list filters by status and handler and applies the limit`() = runTest {
        h2Store().use { fixture ->
            assertTrue(fixture.store.create(definition("one"), Unit, serializer(), now))
            assertTrue(
                fixture.store.create(
                    definition("two", schedule = Schedule.Once(now + 1.seconds)),
                    Unit, serializer(), now + 1.seconds,
                ),
            )
            assertTrue(
                fixture.store.create(
                    definition("other", handlerId = "other", schedule = Schedule.Once(now + 2.seconds)),
                    Unit, serializer(), now + 2.seconds,
                ),
            )
            assertNotNull(fixture.store.claim(request(now)))

            assertEquals(
                listOf("one", "other", "two"),
                fixture.store.list().map { it.definition.id.value },
            )
            assertEquals(
                listOf("one"),
                fixture.store.list(JobStore.Filter(statuses = setOf(Job.Status.RUNNING))).map { it.definition.id.value },
            )
            assertEquals(
                listOf("other"),
                fixture.store.list(JobStore.Filter(handlerId = "other")).map { it.definition.id.value },
            )
            assertEquals(2, fixture.store.list(JobStore.Filter(limit = 2)).size)
        }
    }

    @Test
    fun `only one concurrent claim succeeds`() = runTest {
        h2Store().use { fixture ->
            val definition = definition("concurrent")
            assertTrue(fixture.store.create(definition, Unit, serializer(), now))

            val claims = coroutineScope {
                List(10) {
                    async { fixture.store.claim(request(now)) }
                }.awaitAll()
            }

            assertEquals(1, claims.count { it != null })
            val job = assertNotNull(fixture.store.get(definition.id))
            assertEquals(Job.Status.RUNNING, job.status)
            assertEquals(1, job.attempt)
        }
    }

    @Test
    fun `lease transitions require the owning token`() = runTest {
        h2Store().use { fixture ->
            val definition = definition("token")
            assertTrue(fixture.store.create(definition, Unit, serializer(), now))
            val claim = assertNotNull(fixture.store.claim(request(now, lease = 1.minutes)))
            val stale = claim.copy(
                job = claim.job.copy(lock = checkNotNull(claim.job.lock).copy(token = "stale")),
            )

            assertFalse(fixture.store.renew(stale, now, 1.minutes))
            assertFalse(fixture.store.complete(stale, now, null))
            assertFalse(fixture.store.fail(stale, now, "nope"))

            assertTrue(fixture.store.renew(claim, now, 1.minutes))
            assertTrue(fixture.store.complete(claim, now, null))
            assertEquals(Job.Status.COMPLETED, assertNotNull(fixture.store.get(definition.id)).status)
        }
    }

    @Test
    fun `expired lease can be rescheduled`() = runTest {
        h2Store().use { fixture ->
            val definition = definition("expired")
            assertTrue(fixture.store.create(definition, Unit, serializer(), now))
            val claim = assertNotNull(fixture.store.claim(request(now, lease = 10.seconds)))

            val expired = fixture.store.expired(now + 11.seconds)
            assertEquals(listOf(claim.job.definition.id), expired.map { it.job.definition.id })
            assertTrue(expired.single().recovered)

            val retryAt = now + 30.seconds
            assertTrue(fixture.store.reschedule(claim, now + 11.seconds, retryAt, "lease expired"))
            assertNull(fixture.store.claim(request(now + 12.seconds, lease = 10.seconds)))
            assertNotNull(fixture.store.claim(request(retryAt, lease = 10.seconds)))
        }
    }

    @Test
    fun `updateDefinition preserves runtime state and resets a changed schedule`() = runTest {
        h2Store().use { fixture ->
            val definition = definition("update")
            assertTrue(fixture.store.create(definition, "before", serializer(), now))
            val claim = assertNotNull(fixture.store.claim(request(now, lease = 1.minutes)))
            assertTrue(fixture.store.reschedule(claim, now, now + 10.seconds, "retry"))

            assertTrue(fixture.store.updateDefinition(definition, "after", serializer()))
            val preserved = assertNotNull(fixture.store.get(definition.id))
            assertEquals(Job.Status.PENDING, preserved.status)
            assertEquals(1, preserved.attempt)
            assertEquals(now + 10.seconds, preserved.nextRun)
            assertEquals("after", fixture.store.readConfig(claim, serializer()))

            val changed = definition.copy(schedule = Schedule.FixedRate(1.minutes))
            val nextRunAt = now + 1.minutes
            assertTrue(fixture.store.updateDefinition(changed, "changed", serializer(), nextRunAt))
            val updated = assertNotNull(fixture.store.get(definition.id))
            assertEquals(Job.Status.PENDING, updated.status)
            assertEquals(0, updated.attempt)
            assertEquals(nextRunAt, updated.nextRun)
        }
    }

    @Test
    fun `delete rejects a running job`() = runTest {
        h2Store().use { fixture ->
            val definition = definition("delete")
            assertTrue(fixture.store.create(definition, Unit, serializer(), now))
            val claim = assertNotNull(fixture.store.claim(request(now)))

            assertFalse(fixture.store.delete(definition.id))
            assertTrue(fixture.store.complete(claim, now, null))
            assertTrue(fixture.store.delete(definition.id))
            assertNull(fixture.store.get(definition.id))
        }
    }

    @Test
    fun `worker executes a job through the exposed store`() = runBlocking {
        h2Store().use { fixture ->
            val executed = CompletableDeferred<String>()
            val worker = Worker(id = "exposed-worker", store = fixture.store) {
                jobs {
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
        sqliteStore().use { fixture ->
            val definition = definition("sqlite")
            assertTrue(fixture.store.create(definition, "value", serializer(), now))

            val claim = assertNotNull(fixture.store.claim(request(now)))
            assertEquals("value", fixture.store.readConfig(claim, serializer()))
            assertTrue(fixture.store.complete(claim, now, null))
            assertEquals(Job.Status.COMPLETED, assertNotNull(fixture.store.get(definition.id)).status)
        }
    }

    private fun h2Store(): StoreFixture {
        val database = Database.connect(
            "jdbc:h2:mem:worker_${Uuid.random()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction(database) { SchemaUtils.create(WorkerJobsTable) }
        return StoreFixture(ExposedJobStore(database, TestJson))
    }

    private fun sqliteStore(): StoreFixture {
        val path = Files.createTempFile("worker-exposed-", ".sqlite")
        val database = Database.connect(
            "jdbc:sqlite:${path.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )
        transaction(database) { SchemaUtils.create(WorkerJobsTable) }
        return StoreFixture(ExposedJobStore(database, TestJson), path)
    }

    private fun definition(
        id: String,
        handlerId: String = "handler",
        schedule: Schedule = Schedule.Once(now),
        retry: RetryPolicy = RetryPolicy(),
        latePolicy: LateExecutionPolicy = LateExecutionPolicy.Run,
    ): Job.Definition = Job.Definition(
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
    ): JobStore.ClaimRequest = JobStore.ClaimRequest(
        now = at,
        supportedHandlerIds = supported,
        prepareWindow = 1.seconds,
        lease = lease,
        workerId = "worker",
    )

    private class StoreFixture(
        val store: ExposedJobStore,
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
