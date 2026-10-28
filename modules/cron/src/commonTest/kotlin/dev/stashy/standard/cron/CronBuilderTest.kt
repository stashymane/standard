package dev.stashy.standard.cron

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CronBuilderTest {
    @Test
    fun `builds segments as written`() {
        assertEquals(
            "0 * * ? * * 2026-2028",
            Cron { years { between(2026, 2028) } }.expression,
        )
        assertEquals(
            "0 * * ? * * 2026,2027,2028,2031",
            Cron { years { on(2026, 2027, 2028, 2031) } }.expression,
        )
    }

    @Test
    fun `builds defaults`() {
        assertEquals(
            Cron.parse("0 * * ? * * *"),
            Cron {},
        )
    }

    @Test
    fun `builds the same value as parsing`() {
        val built = Cron {
            seconds { at(0) }
            minutes { every(15) }
            hours { between(9, 17) }
            daysOfWeek { between(DayOfWeek.MONDAY, DayOfWeek.FRIDAY) }
            months { on(Month.JANUARY, Month.MARCH, Month.JULY) }
            years { on(2026) }
        }

        assertEquals(
            Cron.parse("0 */15 9-17 ? JAN,MAR,JUL MON-FRI 2026"),
            built,
        )
    }

    @Test
    fun `builds day-of-month specials`() {
        assertEquals(
            "0 * * L * ? *",
            Cron { daysOfMonth { last() } }.expression,
        )
        assertEquals(
            "0 * * L-2 * ? *",
            Cron { daysOfMonth { last(2) } }.expression,
        )
        assertEquals(
            "0 * * LW * ? *",
            Cron { daysOfMonth { lastWeekday() } }.expression,
        )
        assertEquals(
            "0 * * 10W * ? *",
            Cron { daysOfMonth { nearestWeekday(10) } }.expression,
        )
    }

    @Test
    fun `builds day-of-week steps using cron ordering`() {
        assertEquals(
            "0 * * ? * */2 *",
            Cron { daysOfWeek { every(2) } }.expression,
        )
    }

    @Test
    fun `builds day-of-week specials`() {
        assertEquals(
            "0 * * ? * MON#1 *",
            Cron { daysOfWeek { nth(1, DayOfWeek.MONDAY) } }.expression,
        )
        assertEquals(
            "0 * * ? * FRIL *",
            Cron { daysOfWeek { last(DayOfWeek.FRIDAY) } }.expression,
        )
    }

    @Test
    fun `every day-of-week value set round trips`() {
        val days = DayOfWeek.entries

        for (mask in 1..127) {
            val selected = days.filterIndexed { index, _ -> (mask shr index) and 1 == 1 }
            val expression = Cron { daysOfWeek { on(*selected.toTypedArray()) } }

            assertEquals(
                expression,
                Cron.parse(expression.expression),
                expression.expression,
            )
        }
    }

    @Test
    fun `rejects conflicting day selectors`() {
        assertFailsWith<IllegalStateException> {
            Cron {
                daysOfMonth { on(1) }
                daysOfWeek { on(DayOfWeek.MONDAY) }
            }
        }
    }

    @Test
    fun `rejects conflicting specials and values`() {
        assertFailsWith<IllegalStateException> {
            Cron { daysOfMonth { on(1); last() } }
        }
        assertFailsWith<IllegalStateException> {
            Cron { daysOfWeek { on(DayOfWeek.MONDAY); nth(1, DayOfWeek.MONDAY) } }
        }
    }

    @Test
    fun `rejects out-of-range values`() {
        assertFailsWith<IllegalArgumentException> {
            Cron { seconds { at(60) } }
        }
        assertFailsWith<IllegalArgumentException> {
            Cron { daysOfMonth { on(32) } }
        }
        assertFailsWith<IllegalArgumentException> {
            Cron { years { on(1_000_000_000) } }
        }
        assertFailsWith<IllegalArgumentException> {
            Cron { daysOfWeek { nth(6, DayOfWeek.MONDAY) } }
        }
    }
}
