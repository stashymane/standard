package dev.stashy.standard.util.time

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A point in time described relative to a reference instant.
 */
@ConsistentCopyVisibility
public data class RelativeTime internal constructor(
    public val components: List<Component>,
    public val past: Boolean,
    internal val resolution: Units,
) {
    /** Whether the described instant is close enough to the reference to be shown as "just now". */
    public val isJustNow: Boolean
        get() = components.isEmpty()

    /** A nonzero amount of a single [unit]. */
    public data class Component(
        public val unit: Units,
        public val amount: Long,
    )

    public enum class Units {
        Seconds,
        Minutes,
        Hours,
        Days,
        Months,
        Years,
    }

    public companion object {
        /**
         * Describes [instant] relative to [now], using at most [maxComponents] components.
         *
         * Seconds are only included when the elapsed time is under five minutes. Months are
         * capped at 11 when years are present.
         */
        public fun of(instant: Instant, now: Instant, maxComponents: Int = 2): RelativeTime {
            require(maxComponents > 0) { "maxComponents must be positive" }

            val elapsed = now - instant
            val amount = elapsed.absoluteValue
            val past = !elapsed.isNegative()

            if (amount < 1.seconds) {
                return RelativeTime(emptyList(), past = true, resolution = Units.Seconds)
            }

            val leading = when {
                amount < 1.minutes -> Units.Seconds
                amount < 1.hours -> Units.Minutes
                amount < 1.days -> Units.Hours
                amount < 30.days -> Units.Days
                amount < 365.days -> Units.Months
                else -> Units.Years
            }

            val values = unitValues(amount)
            val components = mutableListOf<Component>()
            var resolution = leading

            for (unit in units.dropWhile { it != leading }) {
                if (unit == Units.Seconds && amount >= 5.minutes) break

                components += Component(unit, values.getValue(unit))
                resolution = unit
                if (components.size == maxComponents) break
            }

            return RelativeTime(
                components = components.filter { it.amount > 0 },
                past = past,
                resolution = resolution,
            )
        }

        private val units = Units.entries.reversed()

        private fun unitValues(amount: Duration): Map<Units, Long> {
            val days = amount.inWholeDays
            val years = days / 365
            val daysAfterYears = days % 365
            val months = if (years > 0) {
                minOf(daysAfterYears / 30, 11)
            } else {
                days / 30
            }

            return mapOf(
                Units.Years to years,
                Units.Months to months,
                Units.Days to if (years > 0) daysAfterYears % 30 else days % 30,
                Units.Hours to amount.inWholeHours % 24,
                Units.Minutes to amount.inWholeMinutes % 60,
                Units.Seconds to amount.inWholeSeconds % 60,
            )
        }
    }

    /**
     * Milliseconds until this value would change when measured against [instant] at [now].
     */
    public fun millisUntilNextChange(instant: Instant, now: Instant): Long {
        val elapsed = now - instant
        if (isJustNow) {
            return if (elapsed.isNegative()) {
                (-elapsed).inWholeMilliseconds.coerceAtLeast(1)
            } else {
                (1.seconds - elapsed).inWholeMilliseconds.coerceAtLeast(1)
            }
        }

        val elapsedMs = elapsed.absoluteValue.inWholeMilliseconds
        val leading = components.first().unit
        val millis = if (resolution == Units.Months) {
            monthsChangeMillis(elapsedMs)
        } else {
            val width = resolution.width.inWholeMilliseconds
            val remainder = elapsedMs % width
            if (past) {
                val untilNextValue = width - remainder
                val untilNextUnit = leading.end?.let { it.inWholeMilliseconds - elapsedMs } ?: Long.MAX_VALUE
                minOf(untilNextValue, untilNextUnit)
            } else {
                remainder + 1
            }
        }
        return millis.coerceAtLeast(1)
    }

    private fun monthsChangeMillis(elapsedMs: Long): Long {
        val yearMs = Units.Years.width.inWholeMilliseconds
        val monthMs = Units.Months.width.inWholeMilliseconds
        val years = elapsedMs / yearMs
        val yearStart = years * yearMs
        val yearEnd = yearStart + yearMs
        val monthsInYear = (elapsedMs - yearStart) / monthMs

        return if (past) {
            val next = if (years > 0 && monthsInYear >= 11) {
                yearEnd
            } else {
                minOf(yearStart + (monthsInYear + 1) * monthMs, yearEnd)
            }
            next - elapsedMs
        } else {
            val previous = if (years > 0 && monthsInYear >= 11) {
                yearStart + 11 * monthMs
            } else {
                yearStart + monthsInYear * monthMs
            }
            elapsedMs - previous + 1
        }.coerceAtLeast(1)
    }
}

private val RelativeTime.Units.width: Duration
    get() = when (this) {
        RelativeTime.Units.Seconds -> 1.seconds
        RelativeTime.Units.Minutes -> 1.minutes
        RelativeTime.Units.Hours -> 1.hours
        RelativeTime.Units.Days -> 1.days
        RelativeTime.Units.Months -> 30.days
        RelativeTime.Units.Years -> 365.days
    }

private val RelativeTime.Units.end: Duration?
    get() = when (this) {
        RelativeTime.Units.Seconds -> 1.minutes
        RelativeTime.Units.Minutes -> 1.hours
        RelativeTime.Units.Hours -> 1.days
        RelativeTime.Units.Days -> 30.days
        RelativeTime.Units.Months -> 365.days
        RelativeTime.Units.Years -> null
    }
