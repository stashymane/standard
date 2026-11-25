package dev.stashy.worker.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class EarliestTest {
    private val start = Instant.parse("2026-10-27T12:00:00Z")

    @Test
    fun `first occurrence is the earliest child occurrence`() {
        val schedule = Schedule.Earliest(
            listOf(Schedule.FixedRate(10.minutes), Schedule.FixedDelay(1.hours)),
        )

        assertEquals(start + 10.minutes, schedule.firstOccurrence(start))
    }

    @Test
    fun `next occurrence is the earliest child occurrence`() {
        val schedule = Schedule.Earliest(
            listOf(Schedule.FixedDelay(1.hours), Schedule.FixedRate(10.minutes)),
        )

        assertEquals(start + 10.minutes, schedule.nextOccurrence(start, start))
    }

    @Test
    fun `a cron child can win over an interval`() {
        val schedule = Schedule.Earliest(
            listOf(Schedule.FixedRate(1.hours), Schedule.Cron("0 15 12 ? * * *", "UTC")),
        )

        assertEquals(start + 15.minutes, schedule.nextOccurrence(start, start))
    }

    @Test
    fun `an exhausted child is ignored`() {
        val schedule = Schedule.Earliest(
            listOf(Schedule.Once(start), Schedule.FixedRate(1.hours)),
        )

        assertEquals(start + 1.hours, schedule.nextOccurrence(start, start + 1.minutes))
    }

    @Test
    fun `the union ends once every child is exhausted`() {
        val schedule = Schedule.Earliest(
            listOf(Schedule.Once(start), Schedule.Once(start + 1.hours)),
        )

        assertNull(schedule.nextOccurrence(start + 1.hours, start + 2.hours))
    }

    @Test
    fun `nested unions take the earliest occurrence overall`() {
        val schedule = Schedule.Earliest(
            listOf(
                Schedule.Earliest(listOf(Schedule.FixedDelay(1.hours))),
                Schedule.FixedRate(10.minutes),
            ),
        )

        assertEquals(start + 10.minutes, schedule.firstOccurrence(start))
    }

    @Test
    fun `a union does not resolve to a single child`() {
        val schedule = Schedule.Earliest(
            listOf(Schedule.Once(start), Schedule.FixedRate(1.hours)),
        )

        assertEquals(schedule, schedule.activeAt(start))
    }

    @Test
    fun `children must not be empty`() {
        assertFailsWith<IllegalArgumentException> { Schedule.Earliest(emptyList()) }
    }
}
