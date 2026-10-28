package dev.stashy.worker.schedule

import dev.stashy.standard.cron.Cron
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ScheduleTest {
    @Test
    fun `once keeps its instant`() {
        val at = Instant.parse("2026-10-27T12:00:00Z")
        val initial = Schedule.Once(at).firstOccurrence(Instant.parse("2026-10-27T10:00:00Z"))

        assertEquals(at, initial)
        assertEquals(null, Schedule.Once(at).nextOccurrence(at, at + 1.hours))
    }

    @Test
    fun `fixed rate starts after the interval by default`() {
        val now = Instant.parse("2026-10-27T12:00:00Z")
        val schedule = Schedule.FixedRate(1.hours)

        assertEquals(now + 1.hours, schedule.firstOccurrence(now))
    }

    @Test
    fun `fixed rate skips missed occurrences`() {
        val scheduledAt = Instant.parse("2026-10-27T12:00:00Z")
        val schedule = Schedule.FixedRate(1.hours)
        val finishedAt = Instant.parse("2026-10-27T15:30:00Z")

        assertEquals(
            Instant.parse("2026-10-27T16:00:00Z"),
            schedule.nextOccurrence(scheduledAt, finishedAt),
        )
    }

    @Test
    fun `fixed delay waits after completion`() {
        val finishedAt = Instant.parse("2026-10-27T15:30:00Z")
        val schedule = Schedule.FixedDelay(10.seconds)

        assertEquals(
            finishedAt + 10.seconds,
            schedule.nextOccurrence(Instant.parse("2026-10-27T12:00:00Z"), finishedAt),
        )
    }

    @Test
    fun `cron accepts a parsed expression`() {
        val expression = Cron.parse("0 0 3 ? * * *")
        val schedule = Schedule.Cron(expression, "UTC")

        assertEquals(expression, schedule.expression)
    }

    @Test
    fun `cron initial occurrence is next after now`() {
        val now = Instant.parse("2026-10-27T12:30:00Z")
        val schedule = Schedule.Cron("0 0 3 ? * * *", "UTC")

        assertEquals(
            Instant.parse("2026-10-28T03:00:00Z"),
            schedule.firstOccurrence(now),
        )
    }
}
