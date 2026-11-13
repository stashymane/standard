package dev.stashy.standard.util.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RelativeTimeTest {
    private val instant = Instant.fromEpochMilliseconds(0)

    @Test
    fun `just now has no direction`() {
        val future = RelativeTime.of(instant, instant - 500.milliseconds)
        val past = RelativeTime.of(instant, instant + 500.milliseconds)

        assertEquals(RelativeTime(RelativeTime.Unit.JustNow, 0, past = true), future)
        assertEquals(future, past)
    }

    @Test
    fun `past units change on the next value boundary`() {
        assertDelay(500.milliseconds, instant + 500.milliseconds)
        assertDelay(500.milliseconds, instant + 1.seconds + 500.milliseconds)
        assertDelay(1.seconds, instant + 59.seconds)
        assertDelay(48.seconds, instant + 5.minutes + 12.seconds)
        assertDelay(10.seconds, instant + 59.minutes + 50.seconds)
        assertDelay(1.minutes, instant + 59.minutes)
        assertDelay(30.minutes, instant + 23.hours + 30.minutes)
        assertDelay(1.hours, instant + 23.hours)
        assertDelay(12.hours, instant + 29.days + 12.hours)
        assertDelay(1.days, instant + 29.days)
        assertDelay(30.days, instant + 30.days)
        assertDelay(1.days, instant + 359.days)
        assertDelay(5.days, instant + 360.days)
        assertDelay(365.days, instant + 365.days)
    }

    @Test
    fun `future units change on the previous value boundary`() {
        assertDelay(500.milliseconds, instant - 500.milliseconds)
        assertDelay(500.milliseconds + 1.milliseconds, instant - 1.seconds - 500.milliseconds)
        assertDelay(1.milliseconds, instant - 1.seconds)
        assertDelay(1.milliseconds, instant - 59.seconds)
        assertDelay(12.seconds + 1.milliseconds, instant - 5.minutes - 12.seconds)
        assertDelay(58.seconds + 1.milliseconds, instant - 59.minutes - 58.seconds)
        assertDelay(1.milliseconds, instant - 1.minutes)
        assertDelay(1.milliseconds, instant - 1.hours)
        assertDelay(1.milliseconds, instant - 1.days)
        assertDelay(1.milliseconds, instant - 30.days)
        assertDelay(1.milliseconds, instant - 360.days)
        assertDelay(1.milliseconds, instant - 365.days)
        assertDelay(1.days + 1.milliseconds, instant - 366.days)
    }

    private fun assertDelay(expected: Duration, now: Instant) {
        val relative = RelativeTime.of(instant, now)
        assertEquals(expected, relative.millisUntilNextChange(instant, now).milliseconds)
    }
}
