package dev.stashy.standard.cron

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class CronTimeZoneTest {
    private val berlin = TimeZone.of("Europe/Berlin")

    @Test
    fun `evaluates local wall-clock times`() {
        assertEquals(
            Instant.parse("2026-10-28T02:00:00Z"),
            Cron.parse("0 0 3 ? * * *")
                .next(Instant.parse("2026-10-27T12:00:00Z"), berlin),
        )
    }

    @Test
    fun `skips wall-clock times removed by a dst transition`() {
        assertEquals(
            Instant.parse("2026-03-30T00:30:00Z"),
            Cron.parse("0 30 2 ? * * *")
                .next(Instant.parse("2026-03-28T12:00:00Z"), berlin),
        )
    }

    @Test
    fun `fires ambiguous wall-clock times once at the earlier offset`() {
        val expression = Cron.parse("0 30 2 ? * * *")
        val first = expression.next(Instant.parse("2026-10-24T12:00:00Z"), berlin)

        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), first)
        assertEquals(
            Instant.parse("2026-10-26T01:30:00Z"),
            expression.next(first!!, berlin),
        )
    }
}
