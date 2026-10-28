package dev.stashy.worker.job

import dev.stashy.worker.TestJson
import dev.stashy.worker.schedule.Schedule
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class InMemoryJobStoreTest {
    @Test
    fun `only one concurrent claim succeeds`() = runTest {
        val store = InMemoryJobStore(TestJson)
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
    fun `claim assigns a complete lock and completion clears it`() = runTest {
        val store = InMemoryJobStore(TestJson)
        val definition = definition("lock")
        store.create(definition, Unit, serializer(), now)

        val claim = assertNotNull(store.claim(request(now, lease = 1.minutes)))
        val lock = assertNotNull(store.get(definition.id)?.lock)
        assertEquals("runner", lock.lockedBy)
        assertEquals(now + 1.minutes, lock.expiresAt)
        assertEquals(lock.token, claim.lockToken)

        assertTrue(store.complete(claim, now, null))
        assertNull(store.get(definition.id)?.lock)
    }

    @Test
    fun `claim filters by registered handler ids`() = runTest {
        val store = InMemoryJobStore(TestJson)
        val definition = definition("unsupported")
        store.create(definition, Unit, serializer(), now)

        assertNull(store.claim(request(now, supported = setOf("other"))))
        assertNotNull(store.claim(request(now, supported = setOf("worker"))))
    }

    @Test
    fun `expired lease is not claimable until rescheduled`() = runTest {
        val store = InMemoryJobStore(TestJson)
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
    fun `state changes require the owning token`() = runTest {
        val store = InMemoryJobStore(TestJson)
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
    fun `completing a recurring job resets attempts`() = runTest {
        val store = InMemoryJobStore(TestJson)
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
    fun `updating a schedule resets runtime state`() = runTest {
        val store = InMemoryJobStore(TestJson)
        val definition = definition("update")
        store.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(store.claim(request(now, lease = 1.minutes)))
        assertTrue(store.reschedule(claim, now, now + 1.minutes, "retry"))

        val changed = definition.copy(schedule = Schedule.Cron("0 0 3 ? * * *", "UTC"))
        assertTrue(store.updateDefinition(changed, Unit, serializer(), now + 1.minutes))

        val job = assertNotNull(store.get(definition.id))
        assertEquals(Job.Status.PENDING, job.status)
        assertEquals(0, job.attempt)
        assertEquals(now + 1.minutes, job.nextRun)
    }

    @Test
    fun `completed one-shot is not resurrected by an unchanged definition`() = runTest {
        val store = InMemoryJobStore(TestJson)
        val definition = definition("once")
        store.create(definition, Unit, serializer(), now)
        val claim = assertNotNull(store.claim(request(now, lease = 1.minutes)))
        assertTrue(store.complete(claim, now, null))

        assertTrue(store.updateDefinition(definition, Unit, serializer()))

        val job = assertNotNull(store.get(definition.id))
        assertEquals(Job.Status.COMPLETED, job.status)
        assertNull(job.nextRun)
    }

    @Test
    fun `config round trips through the store`() = runTest {
        val store = InMemoryJobStore(TestJson)
        val definition = definition("config")
        store.create(definition, "value", serializer(), now)
        val claim = assertNotNull(store.claim(request(now, lease = 1.minutes)))

        assertEquals("value", store.readConfig(claim, serializer()))
    }

    @Test
    fun `updating a definition replaces its config`() = runTest {
        val store = InMemoryJobStore(TestJson)
        val definition = definition("config-update")
        store.create(definition, "before", serializer(), now)

        assertTrue(store.updateDefinition(definition, "after", serializer()))
        val claim = assertNotNull(store.claim(request(now, lease = 1.minutes)))

        assertEquals("after", store.readConfig(claim, serializer()))
    }

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
        lease: kotlin.time.Duration = 1.minutes,
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
