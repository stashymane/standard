package dev.stashy.worker.schedule

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ScheduleDslTest {
    private val first = Instant.parse("2026-10-27T12:00:00Z")
    private val second = Instant.parse("2026-10-28T12:00:00Z")

    private object Dsl : ScheduleDsl

    @Test
    fun `leaf builders return their schedules`() {
        assertEquals(Schedule.Once(first), Dsl.once(first))
        assertEquals(Schedule.FixedRate(1.hours), Dsl.every(1.hours))
        assertEquals(Schedule.FixedRate(1.hours, first), Dsl.every(1.hours, first))
        assertEquals(Schedule.FixedDelay(10.seconds), Dsl.after(10.seconds))
        assertEquals(
            Schedule.Cron("0 0 3 ? * * *", TimeZone.UTC.id),
            Dsl.cron("0 0 3 ? * * *", TimeZone.UTC),
        )
    }

    @Test
    fun `phased builds consecutive segments`() {
        val schedule = Dsl.phased {
            until(first) { every(1.hours) }
            until(second) { cron("0 0 3 ? * * *", TimeZone.UTC) }
            then { after(10.seconds) }
        }

        assertEquals(
            Schedule.Phased(
                listOf(
                    Schedule.Phased.Phase(null, Schedule.FixedRate(1.hours)),
                    Schedule.Phased.Phase(first, Schedule.Cron("0 0 3 ? * * *", TimeZone.UTC.id)),
                    Schedule.Phased.Phase(second, Schedule.FixedDelay(10.seconds)),
                ),
            ),
            schedule,
        )
    }

    @Test
    fun `a single then segment is valid`() {
        assertEquals(
            Schedule.Phased(listOf(Schedule.Phased.Phase(null, Schedule.FixedRate(1.hours)))),
            Dsl.phased { then { every(1.hours) } },
        )
    }

    @Test
    fun `phased requires a closing segment`() {
        assertFailsWith<IllegalArgumentException> { Dsl.phased {} }
        assertFailsWith<IllegalArgumentException> { Dsl.phased { until(first) { every(1.hours) } } }
    }

    @Test
    fun `until instants must increase`() {
        assertFailsWith<IllegalArgumentException> {
            Dsl.phased {
                until(second) { every(1.hours) }
                until(first) { every(1.hours) }
                then { every(1.hours) }
            }
        }
    }

    @Test
    fun `segments cannot follow then`() {
        assertFailsWith<IllegalArgumentException> {
            Dsl.phased {
                then { every(1.hours) }
                until(first) { every(1.hours) }
            }
        }
    }

    @Test
    fun `earliest unions its arguments`() {
        assertEquals(
            Schedule.Earliest(
                listOf(
                    Schedule.Cron("0 */5 * ? * MON-FRI *", TimeZone.UTC.id),
                    Schedule.Cron("0 0 * ? * SAT,SUN *", TimeZone.UTC.id),
                ),
            ),
            Dsl.earliest(
                Dsl.cron("0 */5 * ? * MON-FRI *", TimeZone.UTC),
                Dsl.cron("0 0 * ? * SAT,SUN *", TimeZone.UTC),
            ),
        )
    }
}
