package dev.stashy.standard.util.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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

        assertTrue(future.isJustNow)
        assertTrue(past.isJustNow)
        assertEquals(future, past)
    }

    @Test
    fun `seconds are only shown within five minutes`() {
        assertEquals(components(Second to 45), componentsAt(45.seconds))
        assertEquals(components(Minute to 4, Second to 59), componentsAt(4.minutes + 59.seconds))
        assertEquals(components(Minute to 5), componentsAt(5.minutes))
        assertEquals(components(Minute to 5), componentsAt(5.minutes + 12.seconds))

        assertDelay(1.milliseconds, instant + 4.minutes + 59.seconds + 999.milliseconds)
        assertDelay(1.minutes, instant + 5.minutes)
    }

    @Test
    fun `zero components are omitted`() {
        assertEquals(components(Hour to 1), componentsAt(1.hours + 30.seconds))
        assertDelay(30.seconds, instant + 1.hours + 30.seconds)
    }

    @Test
    fun `multiple components reflect the elapsed time`() {
        assertEquals(components(Minute to 1, Second to 20), componentsAt(1.minutes + 20.seconds))
        assertEquals(components(Hour to 1, Minute to 20), componentsAt(1.hours + 20.minutes + 30.seconds))
        assertEquals(components(Hour to 1), componentsAt(1.hours))
        assertEquals(components(Day to 1, Hour to 3), componentsAt(1.days + 3.hours))
        assertEquals(components(Day to 1), componentsAt(1.days))
        assertEquals(components(Month to 1, Day to 5), componentsAt(35.days))
        assertEquals(components(Year to 1, Month to 1), componentsAt(400.days))
        assertEquals(components(Year to 1, Month to 11), componentsAt(725.days))
        assertEquals(components(Year to 2), componentsAt(730.days))
    }

    @Test
    fun `max components limits the breakdown`() {
        assertEquals(components(Year to 1), componentsAt(400.days, maxComponents = 1))
        assertEquals(
            components(Year to 1, Month to 1, Day to 5),
            componentsAt(400.days, maxComponents = 3),
        )
        assertEquals(
            components(Hour to 1, Minute to 20),
            componentsAt(1.hours + 20.minutes + 30.seconds, maxComponents = 3),
        )
        assertEquals(
            components(Day to 1, Hour to 3, Minute to 20),
            componentsAt(1.days + 3.hours + 20.minutes + 30.seconds, maxComponents = 5),
        )
    }

    @Test
    fun `future values have no direction in components`() {
        val future = RelativeTime.of(instant, instant - (1.hours + 20.minutes))

        assertEquals(components(Hour to 1, Minute to 20), future.components)
        assertFalse(future.past)
    }

    @Test
    fun `past values change on the next component boundary`() {
        assertDelay(500.milliseconds, instant + 500.milliseconds)
        assertDelay(500.milliseconds, instant + 1.seconds + 500.milliseconds)
        assertDelay(1.seconds, instant + 59.seconds)
        assertDelay(1.seconds, instant + 4.minutes + 12.seconds)
        assertDelay(48.seconds, instant + 5.minutes + 12.seconds)
        assertDelay(10.seconds, instant + 59.minutes + 50.seconds)
        assertDelay(1.minutes, instant + 59.minutes)
        assertDelay(1.minutes, instant + 23.hours + 30.minutes)
        assertDelay(1.minutes, instant + 23.hours)
        assertDelay(1.hours, instant + 29.days + 12.hours)
        assertDelay(1.hours, instant + 29.days)
        assertDelay(1.days, instant + 30.days)
        assertDelay(1.days, instant + 359.days)
        assertDelay(1.days, instant + 360.days)
        assertDelay(30.days, instant + 365.days)
        assertDelay(30.days, instant + 700.days)
        assertDelay(5.days, instant + 725.days)
        assertDelay(30.days, instant + 730.days)
    }

    @Test
    fun `future values change on the previous component boundary`() {
        assertDelay(500.milliseconds, instant - 500.milliseconds)
        assertDelay(500.milliseconds + 1.milliseconds, instant - 1.seconds - 500.milliseconds)
        assertDelay(1.milliseconds, instant - 1.seconds)
        assertDelay(1.milliseconds, instant - 59.seconds)
        assertDelay(1.milliseconds, instant - 4.minutes - 12.seconds)
        assertDelay(12.seconds + 1.milliseconds, instant - 5.minutes - 12.seconds)
        assertDelay(50.seconds + 1.milliseconds, instant - 59.minutes - 50.seconds)
        assertDelay(1.milliseconds, instant - 59.minutes)
        assertDelay(1.milliseconds, instant - 23.hours - 30.minutes)
        assertDelay(1.milliseconds, instant - 23.hours)
        assertDelay(1.milliseconds, instant - 29.days - 12.hours)
        assertDelay(1.milliseconds, instant - 29.days)
        assertDelay(1.milliseconds, instant - 30.days)
        assertDelay(1.milliseconds, instant - 359.days)
        assertDelay(1.milliseconds, instant - 360.days)
        assertDelay(1.milliseconds, instant - 365.days)
        assertDelay(15.days + 1.milliseconds, instant - 500.days)
        assertDelay(5.days + 1.milliseconds, instant - 700.days)
        assertDelay(30.days + 1.milliseconds, instant - 725.days)
        assertDelay(1.milliseconds, instant - 730.days)
    }

    private fun componentsAt(elapsed: Duration, maxComponents: Int = 2): List<RelativeTime.Component> =
        RelativeTime.of(instant, instant + elapsed, maxComponents).components

    private fun components(vararg components: Pair<RelativeTime.Units, Long>): List<RelativeTime.Component> =
        components.map { (unit, amount) -> RelativeTime.Component(unit, amount) }

    private fun assertDelay(expected: Duration, now: Instant, maxComponents: Int = 2) {
        val relative = RelativeTime.of(instant, now, maxComponents)
        assertEquals(expected, relative.millisUntilNextChange(instant, now).milliseconds)
    }

    private companion object {
        val Second = RelativeTime.Units.Seconds
        val Minute = RelativeTime.Units.Minutes
        val Hour = RelativeTime.Units.Hours
        val Day = RelativeTime.Units.Days
        val Month = RelativeTime.Units.Months
        val Year = RelativeTime.Units.Years
    }
}
