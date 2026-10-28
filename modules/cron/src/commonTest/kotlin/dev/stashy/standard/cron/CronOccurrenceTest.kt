package dev.stashy.standard.cron

import kotlinx.datetime.*
import kotlin.test.*
import kotlin.time.Instant

class CronOccurrenceTest {
    @Test
    fun `finds the next second`() {
        val expression = Cron.parse("*/15 * * ? * * *")

        assertEquals(
            LocalDateTime.parse("2026-10-27T12:30:45"),
            expression.next(LocalDateTime.parse("2026-10-27T12:30:44")),
        )
        assertEquals(
            LocalDateTime.parse("2026-10-27T12:31:00"),
            expression.next(LocalDateTime.parse("2026-10-27T12:30:45")),
        )
    }

    @Test
    fun `finds the next value in a stepped field`() {
        val expression = Cron.parse("0 5-45/10 * ? * * *")

        assertEquals(
            LocalDateTime.parse("2026-10-27T12:15:00"),
            expression.next(LocalDateTime.parse("2026-10-27T12:06:00")),
        )
        assertEquals(
            LocalDateTime.parse("2026-10-27T13:05:00"),
            expression.next(LocalDateTime.parse("2026-10-27T12:50:00")),
        )
    }

    @Test
    fun `finds the nearest value across unsorted segments`() {
        val expression = Cron.parse("0 30,0-10 * ? * * *")

        assertEquals(
            LocalDateTime.parse("2026-10-27T12:01:00"),
            expression.next(LocalDateTime.parse("2026-10-27T12:00:00")),
        )
        assertEquals(
            LocalDateTime.parse("2026-10-27T12:30:00"),
            expression.next(LocalDateTime.parse("2026-10-27T12:10:00")),
        )
    }

    @Test
    fun `finds the next day and respects weekdays`() {
        val daily = Cron.parse("0 0 3 ? * * *")
        assertEquals(
            LocalDateTime.parse("2026-10-28T03:00:00"),
            daily.next(LocalDateTime.parse("2026-10-27T12:00:00")),
        )

        val weekdays = Cron.parse("0 0 3 ? * MON-FRI *")
        assertEquals(
            LocalDateTime.parse("2026-11-02T03:00:00"),
            weekdays.next(LocalDateTime.parse("2026-10-30T10:00:00")),
        )
    }

    @Test
    fun `respects explicit year fields`() {
        val expression = Cron.parse("0 0 0 1 JAN ? 2027")

        assertEquals(
            LocalDateTime.parse("2027-01-01T00:00:00"),
            expression.next(LocalDateTime.parse("2026-10-27T00:00:00")),
        )
        assertNull(expression.next(LocalDateTime.parse("2027-01-02T00:00:00")))
    }

    @Test
    fun `advances to the next year when no month remains`() {
        assertEquals(
            LocalDateTime.parse("2027-01-01T00:00:00"),
            Cron.parse("0 0 0 1 JAN ? *")
                .next(LocalDateTime.parse("2026-02-01T00:00:00")),
        )
    }

    @Test
    fun `handles the end of the supported date range`() {
        assertNull(
            Cron.parse("0 0 0 1 JAN ? *")
                .next(LocalDateTime(999_999_999, 12, 31, 23, 59, 59)),
        )
        assertEquals(
            LocalDateTime(999_999_999, 12, 31, 0, 0),
            Cron.parse("0 0 0 L * ? *")
                .next(LocalDateTime(999_999_999, 12, 30, 0, 0)),
        )
    }

    @Test
    fun `limits the next search to ten years from the input`() {
        val from = LocalDateTime.parse("2026-01-01T00:00:00")

        assertEquals(
            LocalDateTime.parse("2036-01-01T00:00:00"),
            Cron.parse("0 0 0 1 JAN ? 2036").next(from),
        )
        assertNull(
            Cron.parse("0 0 0 1 JAN ? 2037").next(from),
        )
    }

    @Test
    fun `returns null when the date cannot occur`() {
        assertNull(
            Cron.parse("0 0 0 30 FEB ? *")
                .next(LocalDateTime.parse("2026-01-01T00:00:00")),
        )
    }

    @Test
    fun `finds years beyond the previous upper bound`() {
        assertEquals(
            LocalDateTime.parse("2100-01-01T00:00:00"),
            Cron.parse("0 0 0 1 JAN ? 2100")
                .next(LocalDateTime.parse("2095-01-01T00:00:00")),
        )
    }

    @Test
    fun `handles leap day`() {
        assertEquals(
            LocalDateTime.parse("2028-02-29T00:00:00"),
            Cron.parse("0 0 0 29 FEB ? *")
                .next(LocalDateTime.parse("2026-10-27T00:00:00")),
        )
    }

    @Test
    fun `handles last day and last offset`() {
        val last = Cron.parse("0 0 0 L * ? *")
        assertEquals(
            LocalDateTime.parse("2026-10-31T00:00:00"),
            last.next(LocalDateTime.parse("2026-10-01T00:00:00")),
        )

        val lastOffset = Cron.parse("0 0 0 L-3 * ? *")
        assertEquals(
            LocalDateTime.parse("2026-10-28T00:00:00"),
            lastOffset.next(LocalDateTime.parse("2026-10-01T00:00:00")),
        )
    }

    @Test
    fun `handles last weekday`() {
        assertEquals(
            LocalDateTime.parse("2026-10-30T00:00:00"),
            Cron.parse("0 0 0 LW * ? *")
                .next(LocalDateTime.parse("2026-10-01T00:00:00")),
        )
    }

    @Test
    fun `handles nearest weekday`() {
        assertEquals(
            LocalDateTime.parse("2026-08-14T00:00:00"),
            Cron.parse("0 0 0 15W * ? *")
                .next(LocalDateTime.parse("2026-08-01T00:00:00")),
        )
        assertEquals(
            LocalDateTime.parse("2026-08-03T00:00:00"),
            Cron.parse("0 0 0 1W * ? *")
                .next(LocalDateTime.parse("2026-08-01T00:00:00")),
        )
    }

    @Test
    fun `handles nth and last weekdays`() {
        assertEquals(
            LocalDateTime.parse("2026-10-05T00:00:00"),
            Cron.parse("0 0 0 ? * MON#1 *")
                .next(LocalDateTime.parse("2026-10-01T00:00:00")),
        )
        assertEquals(
            LocalDateTime.parse("2026-10-30T00:00:00"),
            Cron.parse("0 0 0 ? * FRIL *")
                .next(LocalDateTime.parse("2026-10-01T00:00:00")),
        )
    }

    @Test
    fun `jumps directly to the next matching date`() {
        fun next(expression: String, from: String): LocalDateTime? =
            Cron.parse(expression).next(LocalDateTime.parse(from))

        assertEquals(
            LocalDateTime.parse("2026-10-20T00:00:00"),
            next("0 0 0 15,20 * ? *", "2026-10-16T00:00:00"),
        )
        assertEquals(
            LocalDateTime.parse("2026-03-31T00:00:00"),
            next("0 0 0 31 * ? *", "2026-02-01T00:00:00"),
        )
        assertEquals(
            LocalDateTime.parse("2026-03-31T00:00:00"),
            next("0 0 0 31W * ? *", "2026-02-01T00:00:00"),
        )
        assertEquals(
            LocalDateTime.parse("2026-11-02T00:00:00"),
            next("0 0 0 ? * MON *", "2026-10-30T00:00:00"),
        )
    }

    @Test
    fun `jumps from a matched date to the next month`() {
        fun next(expression: String, from: String): LocalDateTime? =
            Cron.parse(expression).next(LocalDateTime.parse(from))

        assertEquals(
            LocalDateTime.parse("2026-11-30T00:00:00"),
            next("0 0 0 L * ? *", "2026-10-31T00:00:00"),
        )
        assertEquals(
            LocalDateTime.parse("2026-11-30T00:00:00"),
            next("0 0 0 LW * ? *", "2026-10-30T00:00:00"),
        )
        assertEquals(
            LocalDateTime.parse("2026-11-27T00:00:00"),
            next("0 0 0 L-3 * ? *", "2026-10-28T00:00:00"),
        )
        assertEquals(
            LocalDateTime.parse("2026-09-15T00:00:00"),
            next("0 0 0 15W * ? *", "2026-08-14T00:00:00"),
        )
        assertEquals(
            LocalDateTime.parse("2026-11-30T00:00:00"),
            next("0 0 0 ? * MON#5 *", "2026-08-31T00:00:00"),
        )
        assertEquals(
            LocalDateTime.parse("2026-11-27T00:00:00"),
            next("0 0 0 ? * FRIL *", "2026-10-30T00:00:00"),
        )
    }

    @Test
    fun `date jumps agree with daily scanning`() {
        val expressions = listOf(
            "0 0 0 L * ? *",
            "0 0 0 LW * ? *",
            "0 0 0 L-3 * ? *",
            "0 0 0 15W * ? *",
            "0 0 0 31W * ? *",
            "0 0 0 ? * MON#5 *",
            "0 0 0 ? * FRIL *",
            "0 0 0 15,20 * ? *",
            "0 0 0 31 * ? *",
            "0 0 0 29 FEB ? *",
            "0 0 0 ? * MON-FRI *",
            "0 0 0 1 JAN ? *",
        )
        val starts = listOf(
            "2026-01-01T00:00:00",
            "2026-01-31T00:00:00",
            "2026-02-01T00:00:00",
            "2026-02-28T00:00:00",
            "2026-12-31T00:00:00",
        )

        for (expression in expressions) {
            val cron = Cron.parse(expression)
            for (start in starts) {
                val from = LocalDateTime.parse(start)
                assertEquals(
                    scanNextDate(cron, from, maxDays = 2_000),
                    cron.next(from),
                    "$expression from $start",
                )
            }
        }
    }

    private fun scanNextDate(
        expression: Cron,
        from: LocalDateTime,
        maxDays: Int,
    ): LocalDateTime? {
        var date = from.date
        repeat(maxDays) {
            date = date.plus(1, DateTimeUnit.DAY)
            val candidate = date.atTime(0, 0)
            if (expression.matches(candidate)) return candidate
        }
        return null
    }

    @Test
    fun `matches wall-clock times`() {
        val expression = Cron.parse("0 0 3 ? * * *")

        assertTrue(expression.matches(LocalDateTime.parse("2026-10-28T03:00:00")))
        assertFalse(expression.matches(LocalDateTime.parse("2026-10-28T03:00:01")))
    }

    @Test
    fun `evaluates instants in a time zone`() {
        val expression = Cron.parse("0 0 3 ? * * *")

        assertEquals(
            Instant.parse("2026-10-28T03:00:00Z"),
            expression.next(Instant.parse("2026-10-27T12:00:00Z"), TimeZone.UTC),
        )
    }

    @Test
    fun `asSequence yields wall-clock occurrences`() {
        val values = Cron.parse("0 0 3 ? * * *")
            .asSequence(LocalDateTime.parse("2026-10-27T12:00:00"))
            .take(2)
            .toList()

        assertEquals(
            listOf(
                LocalDateTime.parse("2026-10-28T03:00:00"),
                LocalDateTime.parse("2026-10-29T03:00:00"),
            ),
            values,
        )
    }

    @Test
    fun `asSequence yields occurrences`() {
        val expression = Cron.parse("0 0 3 ? * * *")
        val values = expression
            .asSequence(Instant.parse("2026-10-27T12:00:00Z"), TimeZone.UTC)
            .take(3)
            .toList()

        assertEquals(
            listOf(
                Instant.parse("2026-10-28T03:00:00Z"),
                Instant.parse("2026-10-29T03:00:00Z"),
                Instant.parse("2026-10-30T03:00:00Z"),
            ),
            values,
        )
    }
}
