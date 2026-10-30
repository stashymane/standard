package dev.stashy.worker.job

import dev.stashy.worker.schedule.Schedule
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class JobTest {
    @Test
    fun `running jobs require a lock`() {
        assertFailsWith<IllegalArgumentException> {
            job(status = Job.Status.RUNNING)
        }
    }

    @Test
    fun `non-running jobs cannot have a lock`() {
        assertFailsWith<IllegalArgumentException> {
            job(status = Job.Status.PENDING, lock = lock())
        }
    }

    @Test
    fun `locks require a token and owner`() {
        assertFailsWith<IllegalArgumentException> { Job.Lock("", "runner", now) }
        assertFailsWith<IllegalArgumentException> { Job.Lock("token", "", now) }
    }

    private fun job(status: Job.Status, lock: Job.Lock? = null): Job = Job(
        definition = Job.Definition(
            id = Job.Id("job"),
            handlerId = "worker",
            schedule = Schedule.Once(now),
        ),
        status = status,
        scheduledAt = now,
        nextRun = now,
        attempt = 0,
        lock = lock,
    )

    private fun lock(): Job.Lock = Job.Lock("token", "runner", now + 1.seconds)

    private companion object {
        val now: Instant = Instant.parse("2026-10-27T12:00:00Z")
    }
}
