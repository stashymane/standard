package dev.stashy.worker.job

import dev.stashy.worker.TestJson
import dev.stashy.worker.create
import dev.stashy.worker.schedule.Schedule
import dev.stashy.worker.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class InMemoryJobQueueTest {
    @Test
    fun `only one concurrent claim succeeds`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        val definition = definition("one")
        queue.create(definition, Unit, serializer(), now)

        val claims = coroutineScope {
            List(10) {
                async {
                    queue.claim(request(now, lease = 1.minutes))
                }
            }.awaitAll()
        }

        assertEquals(1, claims.count { it != null })
        assertEquals(1, queue.get(definition.id)?.attempt)
        assertEquals(QueuedJob.Status.RUNNING, queue.get(definition.id)?.status)
    }

    @Test
    fun `claim assigns a complete lock and completion removes the job`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        val definition = definition("lock")
        queue.create(definition, Unit, serializer(), now)

        val claim = assertNotNull(queue.claim(request(now, lease = 1.minutes)))
        val lock = assertNotNull(queue.get(definition.id)?.lock)
        assertEquals("runner", lock.lockedBy)
        assertEquals(now + 1.minutes, lock.expiresAt)
        assertEquals(lock.token, claim.lockToken)

        assertTrue(queue.complete(claim, null))
        assertNull(queue.get(definition.id))
    }

    @Test
    fun `claim filters by registered handler ids`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        val definition = definition("unsupported")
        queue.create(definition, Unit, serializer(), now)

        assertNull(queue.claim(request(now, supported = setOf("other"))))
        assertNotNull(queue.claim(request(now, supported = setOf("worker"))))
    }

    @Test
    fun `expired lease is not claimable until rescheduled`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        val definition = definition("lease")
        queue.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(queue.claim(request(now, lease = 10.seconds)))

        val expired = queue.expired(now + 11.seconds)
        assertEquals(listOf(claim.job.definition.id), expired.map { it.job.definition.id })

        val retryAt = now + 30.seconds
        assertTrue(queue.reschedule(claim, retryAt))
        assertNull(queue.claim(request(now + 12.seconds, lease = 10.seconds)))
        assertNotNull(queue.claim(request(retryAt, lease = 10.seconds)))
    }

    @Test
    fun `claim takes the earliest due jobs up to the limit`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        listOf("a", "b", "c").forEach {
            queue.create(definition(it), Unit, serializer(), now)
        }

        val claims = queue.claim(request(now, lease = 1.minutes), limit = 2)

        assertEquals(listOf("a", "b"), claims.map { it.job.definition.id.value })
        assertTrue(claims.all { it.job.status == QueuedJob.Status.RUNNING })
        assertEquals(QueuedJob.Status.PENDING, queue.get(Job.Id("c"))?.status)
    }

    @Test
    fun `list filters by status and handler and applies the limit`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        queue.create(definition("one"), Unit, serializer(), now)
        queue.create(definition("two"), Unit, serializer(), now)
        queue.create(definition("other").copy(handlerId = "other"), Unit, serializer(), now)
        queue.claim(request(now, lease = 1.minutes))

        assertEquals(
            listOf("one", "other", "two"),
            queue.list().map { it.definition.id.value },
        )
        assertEquals(
            listOf("one"),
            queue.list(JobQueue.Filter(statuses = setOf(QueuedJob.Status.RUNNING))).map { it.definition.id.value },
        )
        assertEquals(
            listOf("other"),
            queue.list(JobQueue.Filter(handlerId = "other")).map { it.definition.id.value },
        )
        assertEquals(2, queue.list(JobQueue.Filter(limit = 2)).size)
    }

    @Test
    fun `state changes require the owning token`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        val definition = definition("token")
        queue.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(queue.claim(request(now, lease = 1.minutes)))
        val stale = claim.copy(
            job = claim.job.copy(lock = checkNotNull(claim.job.lock).copy(token = "stale")),
        )

        assertFalse(queue.renew(stale, now, 1.minutes))
        assertFalse(queue.complete(stale, null))
        assertFalse(queue.fail(stale))
        assertTrue(queue.renew(claim, now, 1.minutes))
    }

    @Test
    fun `completing a recurring job resets attempts`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        val definition = definition("recurring", schedule = Schedule.FixedRate(1.minutes))
        queue.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(queue.claim(request(now, lease = 1.minutes)))

        val next = now + 1.minutes
        assertTrue(queue.complete(claim, next))

        val job = assertNotNull(queue.get(definition.id))
        assertEquals(QueuedJob.Status.PENDING, job.status)
        assertEquals(0, job.attempt)
        assertEquals(next, job.nextRun)
        assertEquals(next, job.scheduledAt)
    }

    @Test
    fun `updating a schedule resets runtime state`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        val definition = definition("update")
        queue.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(queue.claim(request(now, lease = 1.minutes)))
        assertTrue(queue.reschedule(claim, now + 1.minutes))

        val changed = definition.copy(schedule = Schedule.Cron("0 0 3 ? * * *", "UTC"))
        assertTrue(queue.update(changed, Unit, serializer(), now + 1.minutes))

        val job = assertNotNull(queue.get(definition.id))
        assertEquals(QueuedJob.Status.PENDING, job.status)
        assertEquals(0, job.attempt)
        assertEquals(now + 1.minutes, job.nextRun)
    }

    @Test
    fun `completed one-shot job is forgotten`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        val definition = definition("once")
        queue.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(queue.claim(request(now, lease = 1.minutes)))

        assertTrue(queue.complete(claim, null))

        assertNull(queue.get(definition.id))
        assertTrue(queue.list().isEmpty())
    }

    @Test
    fun `failed job is forgotten`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        val definition = definition("failed")
        queue.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(queue.claim(request(now, lease = 1.minutes)))

        assertTrue(queue.fail(claim))

        assertNull(queue.get(definition.id))
        assertTrue(queue.list().isEmpty())
    }

    @Test
    fun `config round trips through the queue`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        val definition = definition("config")
        queue.create(definition, "value", serializer(), now)
        val claim = assertNotNull(queue.claim(request(now, lease = 1.minutes)))

        assertEquals("value", queue.codec.decode(claim.job.payload, serializer()))
    }

    @Test
    fun `direct codec stores the config instance itself`() = runTest {
        val queue = InMemoryJobQueue(DirectConfigCodec)
        val config = DirectConfig("value")
        queue.create(definition("direct"), config, serializer(), now)
        val claim = assertNotNull(queue.claim(request(now, lease = 1.minutes)))

        assertSame(config, queue.codec.decode(claim.job.payload, serializer()))
    }

    @Test
    fun `updating a definition replaces its config`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        val definition = definition("config-update")
        queue.create(definition, "before", serializer(), now)

        assertTrue(queue.update(definition, "after", serializer()))
        val claim = assertNotNull(queue.claim(request(now, lease = 1.minutes)))

        assertEquals("after", queue.codec.decode(claim.job.payload, serializer()))
    }

    @Test
    fun `updating a forgotten job returns false`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        val definition = definition("pruned")
        queue.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(queue.claim(request(now, lease = 1.minutes)))
        assertTrue(queue.complete(claim, null))

        assertFalse(queue.update(definition, Unit, serializer()))
    }

    @Test
    fun `delete rejects a running job`() = runTest {
        val queue = InMemoryJobQueue(TestJson)
        val definition = definition("delete")
        queue.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(queue.claim(request(now, lease = 1.minutes)))

        assertFalse(queue.delete(definition.id))
        assertTrue(queue.complete(claim, null))
        assertFalse(queue.delete(definition.id))
    }

    private fun definition(
        id: String,
        schedule: Schedule = Schedule.Once(now),
    ): Job = Job(
        id = Job.Id(id),
        handlerId = "worker",
        schedule = schedule,
    )

    private fun request(
        at: Instant,
        supported: Set<String> = setOf("worker"),
        lease: kotlin.time.Duration = 1.minutes,
    ): JobQueue.ClaimRequest = JobQueue.ClaimRequest(
        now = at,
        supportedHandlerIds = supported,
        prepareWindow = 1.seconds,
        lease = lease,
        workerId = "runner",
    )

    private companion object {
        val now: Instant = Instant.parse("2026-10-27T12:00:00Z")
    }
}

@Serializable
private data class DirectConfig(val value: String)
