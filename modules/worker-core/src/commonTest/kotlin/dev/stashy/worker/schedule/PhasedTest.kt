package dev.stashy.worker.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class PhasedTest {
    private val start = Instant.parse("2026-10-27T12:00:00Z")
    private val boundary = start + 20.minutes

    private val hourlyUntilBoundary = Schedule.Phased(
        listOf(
            Schedule.Phased.Phase(null, Schedule.FixedRate(1.hours)),
            Schedule.Phased.Phase(boundary, Schedule.FixedRate(10.minutes)),
        ),
    )

    @Test
    fun `the first phase covers instants before the first boundary`() {
        assertEquals(
            Schedule.FixedRate(1.hours),
            hourlyUntilBoundary.activeAt(start - 1.hours),
        )
    }

    @Test
    fun `a phase covers its own start instant`() {
        assertEquals(Schedule.FixedRate(10.minutes), hourlyUntilBoundary.activeAt(boundary))
    }

    @Test
    fun `nested phases resolve to the innermost schedule`() {
        val schedule = Schedule.Phased(
            listOf(
                Schedule.Phased.Phase(null, Schedule.FixedDelay(10.minutes)),
                Schedule.Phased.Phase(
                    boundary,
                    Schedule.Phased(
                        listOf(
                            Schedule.Phased.Phase(null, Schedule.FixedRate(1.hours)),
                            Schedule.Phased.Phase(start + 40.minutes, Schedule.Once(start + 50.minutes)),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(Schedule.Once(start + 50.minutes), schedule.activeAt(start + 45.minutes))
    }

    @Test
    fun `first occurrence comes from the phase that applies now`() {
        assertEquals(start + 35.minutes, hourlyUntilBoundary.firstOccurrence(start + 25.minutes))
    }

    @Test
    fun `first occurrence is clamped to the phase that follows it`() {
        assertEquals(start + 30.minutes, hourlyUntilBoundary.firstOccurrence(start))
    }

    @Test
    fun `next occurrence keeps the cadence inside a phase`() {
        assertEquals(
            start,
            hourlyUntilBoundary.nextOccurrence(start - 1.hours, start - 55.minutes),
        )
    }

    @Test
    fun `next occurrence never lands on or after the boundary`() {
        assertEquals(
            start + 30.minutes,
            hourlyUntilBoundary.nextOccurrence(start, start + 5.minutes),
        )
    }

    @Test
    fun `a phase that became active starts its own cadence`() {
        assertEquals(
            start + 35.minutes,
            hourlyUntilBoundary.nextOccurrence(start + 15.minutes, start + 25.minutes),
        )
    }

    @Test
    fun `an exhausted phase hands over to the next one`() {
        val schedule = Schedule.Phased(
            listOf(
                Schedule.Phased.Phase(null, Schedule.Once(start)),
                Schedule.Phased.Phase(start + 10.minutes, Schedule.FixedRate(10.minutes)),
            ),
        )

        assertEquals(start + 20.minutes, schedule.nextOccurrence(start, start + 1.minutes))
    }

    @Test
    fun `an exhausted last phase ends the schedule`() {
        val schedule = Schedule.Phased(
            listOf(
                Schedule.Phased.Phase(null, Schedule.FixedRate(10.minutes)),
                Schedule.Phased.Phase(boundary, Schedule.Once(start + 30.minutes)),
            ),
        )

        assertNull(schedule.nextOccurrence(start + 30.minutes, start + 31.minutes))
    }

    @Test
    fun `phases must not be empty`() {
        assertFailsWith<IllegalArgumentException> { Schedule.Phased(emptyList()) }
    }

    @Test
    fun `the first phase must be unbounded`() {
        assertFailsWith<IllegalArgumentException> {
            Schedule.Phased(listOf(Schedule.Phased.Phase(start, Schedule.FixedRate(1.hours))))
        }
    }

    @Test
    fun `only the first phase may be unbounded`() {
        assertFailsWith<IllegalArgumentException> {
            Schedule.Phased(
                listOf(
                    Schedule.Phased.Phase(null, Schedule.FixedRate(1.hours)),
                    Schedule.Phased.Phase(null, Schedule.FixedRate(1.hours)),
                ),
            )
        }
    }

    @Test
    fun `phase starts must increase`() {
        assertFailsWith<IllegalArgumentException> {
            Schedule.Phased(
                listOf(
                    Schedule.Phased.Phase(null, Schedule.FixedRate(1.hours)),
                    Schedule.Phased.Phase(boundary, Schedule.FixedRate(1.hours)),
                    Schedule.Phased.Phase(boundary, Schedule.FixedRate(1.hours)),
                ),
            )
        }
    }
}
