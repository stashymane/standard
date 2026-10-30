package dev.stashy.worker.job

import dev.stashy.worker.schedule.Schedule
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class LocalJobStoreTest {
    @Test
    fun `only one concurrent claim succeeds`() = runTest {
        val store = LocalJobStore()
        val definition = definition("one")
        store.create(definition, Unit, serializer(), now)

        val claims = coroutineScope {
            List(10) {
                async {
                    store.claim(request(now, lease = 1.minutes))
                }
            }.awaitAll()
        }

        assertEquals(1, claims.count { it != null })
        assertEquals(1, store.get(definition.id)?.attempt)
        assertEquals(Job.Status.RUNNING, store.get(definition.id)?.status)
    }

    @Test
    fun `claim assigns a complete lock`() = runTest {
        val store = LocalJobStore()
        val definition = definition("lock")
        store.create(definition, Unit, serializer(), now)

        val claim = assertNotNull(store.claim(request(now, lease = 1.minutes)))
        val lock = assertNotNull(store.get(definition.id)?.lock)
        assertEquals("runner", lock.lockedBy)
        assertEquals(now + 1.minutes, lock.expiresAt)
        assertEquals(lock.token, claim.lockToken)
    }

    @Test
    fun `claim filters by registered handler ids`() = runTest {
        val store = LocalJobStore()
        val definition = definition("unsupported")
        store.create(definition, Unit, serializer(), now)

        assertNull(store.claim(request(now, supported = setOf("other"))))
        assertNotNull(store.claim(request(now, supported = setOf("worker"))))
    }

    @Test
    fun `claim takes the earliest due jobs up to the limit`() = runTest {
        val store = LocalJobStore()
        listOf("a", "b", "c").forEach {
            store.create(definition(it), Unit, serializer(), now)
        }

        val claims = store.claim(request(now, lease = 1.minutes), limit = 2)

        assertEquals(listOf("a", "b"), claims.map { it.job.definition.id.value })
        assertTrue(claims.all { it.job.status == Job.Status.RUNNING })
        assertEquals(Job.Status.PENDING, store.get(Job.Id("c"))?.status)
    }

    @Test
    fun `state changes require the owning token`() = runTest {
        val store = LocalJobStore()
        val definition = definition("token")
        store.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(store.claim(request(now, lease = 1.minutes)))
        val stale = claim.copy(
            job = claim.job.copy(lock = checkNotNull(claim.job.lock).copy(token = "stale")),
        )

        assertFalse(store.renew(stale, now, 1.minutes))
        assertFalse(store.complete(stale, now, null))
        assertFalse(store.fail(stale, now, "nope"))
        assertTrue(store.renew(claim, now, 1.minutes))
    }

    @Test
    fun `expired lease is not claimable until rescheduled`() = runTest {
        val store = LocalJobStore()
        val definition = definition("lease")
        store.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(store.claim(request(now, lease = 10.seconds)))

        val expired = store.expired(now + 11.seconds)
        assertEquals(listOf(claim.job.definition.id), expired.map { it.job.definition.id })

        val retryAt = now + 30.seconds
        assertTrue(store.reschedule(claim, now + 11.seconds, retryAt, "lease expired"))
        assertNull(store.claim(request(now + 12.seconds, lease = 10.seconds)))
        assertNotNull(store.claim(request(retryAt, lease = 10.seconds)))
    }

    @Test
    fun `completed one-shot job is forgotten`() = runTest {
        val store = LocalJobStore()
        val definition = definition("once")
        store.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(store.claim(request(now, lease = 1.minutes)))

        assertTrue(store.complete(claim, now, null))

        assertNull(store.get(definition.id))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `failed job is forgotten`() = runTest {
        val store = LocalJobStore()
        val definition = definition("failed")
        store.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(store.claim(request(now, lease = 1.minutes)))

        assertTrue(store.fail(claim, now, "boom"))

        assertNull(store.get(definition.id))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `completing a recurring job keeps its pending row`() = runTest {
        val store = LocalJobStore()
        val definition = definition("recurring", schedule = Schedule.FixedRate(1.minutes))
        store.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(store.claim(request(now, lease = 1.minutes)))

        val next = now + 1.minutes
        assertTrue(store.complete(claim, now, next))

        val job = assertNotNull(store.get(definition.id))
        assertEquals(Job.Status.PENDING, job.status)
        assertEquals(0, job.attempt)
        assertEquals(next, job.nextRun)
        assertEquals(next, job.scheduledAt)
    }

    @Test
    fun `rescheduled job is kept`() = runTest {
        val store = LocalJobStore()
        val definition = definition("retry")
        store.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(store.claim(request(now, lease = 1.minutes)))

        assertTrue(store.reschedule(claim, now, now + 1.minutes, "retry"))

        val job = assertNotNull(store.get(definition.id))
        assertEquals(Job.Status.PENDING, job.status)
        assertEquals(now + 1.minutes, job.nextRun)
        assertEquals("retry", job.lastError)
    }

    @Test
    fun `config is returned without a serialization round trip`() = runTest {
        val store = LocalJobStore()
        val definition = definition("config")
        val config = Config("value")
        store.create(definition, config, serializer(), now)
        val claim = assertNotNull(store.claim(request(now, lease = 1.minutes)))

        assertSame(config, store.readConfig(claim, serializer()))
    }

    @Test
    fun `updating a definition replaces its config`() = runTest {
        val store = LocalJobStore()
        val definition = definition("config-update")
        store.create(definition, Config("before"), serializer(), now)

        assertTrue(store.updateDefinition(definition, Config("after"), serializer()))
        val claim = assertNotNull(store.claim(request(now, lease = 1.minutes)))

        assertEquals(Config("after"), store.readConfig(claim, serializer()))
    }

    @Test
    fun `updating a forgotten job returns false`() = runTest {
        val store = LocalJobStore()
        val definition = definition("pruned")
        store.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(store.claim(request(now, lease = 1.minutes)))
        assertTrue(store.complete(claim, now, null))

        assertFalse(store.updateDefinition(definition, Unit, serializer()))
    }

    @Serializable
    private data class Config(val value: String)

    private fun definition(
        id: String,
        schedule: Schedule = Schedule.Once(now),
    ): Job.Definition = Job.Definition(
        id = Job.Id(id),
        handlerId = "worker",
        schedule = schedule,
    )

    private fun request(
        at: Instant,
        supported: Set<String> = setOf("worker"),
        lease: Duration = 1.minutes,
    ): JobStore.ClaimRequest = JobStore.ClaimRequest(
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
