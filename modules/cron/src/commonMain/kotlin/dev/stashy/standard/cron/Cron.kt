package dev.stashy.standard.cron

import dev.stashy.standard.cron.fields.CronField
import dev.stashy.standard.cron.fields.DaySelector
import dev.stashy.standard.cron.util.*
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * An immutable modern cron expression.
 *
 * @see parse
 */
@ConsistentCopyVisibility
@Serializable(with = CronSerializer::class)
public data class Cron internal constructor(
    internal val seconds: CronField.Seconds,
    internal val minutes: CronField.Minutes,
    internal val hours: CronField.Hours,
    internal val day: DaySelector,
    internal val months: CronField.Months,
    internal val years: CronField.Years,
) {
    /**
     * The 7-field representation of this expression, preserving segment order.
     */
    public val expression: String by lazy {
        listOf(
            seconds.toExpression(),
            minutes.toExpression(),
            hours.toExpression(),
            day.toDayOfMonth(),
            months.toExpression(),
            day.toDayOfWeek(),
            years.toExpression(),
        ).joinToString(" ")
    }

    /**
     * Returns whether [dateTime] matches this expression. The check is purely wall-clock based.
     */
    public fun matches(dateTime: LocalDateTime): Boolean = when {
        dateTime.second !in seconds -> false
        dateTime.minute !in minutes -> false
        dateTime.hour !in hours -> false
        dateTime.month.number !in months -> false
        dateTime.year !in years -> false
        else -> day.matches(dateTime.date)
    }

    /**
     * Returns whether [instant] matches this expression in [timeZone].
     */
    public fun matches(instant: Instant, timeZone: TimeZone): Boolean =
        matches(instant.toLocalDateTime(timeZone))

    /**
     * Returns the first matching wall-clock time strictly after [from], or `null` when no match is
     * found within [MAX_SEARCH_YEARS] years of [from] or within the supported date range.
     */
    public fun next(from: LocalDateTime): LocalDateTime? {
        if (from.year > CronField.Years.MAX_YEAR) return null

        val lastSearchYear = minOf(
            from.year.toLong() + MAX_SEARCH_YEARS,
            CronField.Years.MAX_YEAR.toLong(),
        ).toInt()
        var candidate = from.plusOneSecond() ?: return null
        while (true) {
            val year = candidate.year
            val month = candidate.month
            val date = candidate.date
            val hour = candidate.hour
            val minute = candidate.minute
            val second = candidate.second

            candidate = when {
                year > lastSearchYear -> return null
                year !in years -> Month.JANUARY.startOfMonth(
                    years.nextOrNull(year) ?: return null,
                )

                month.number !in months -> when (val nextMonth = months.nextOrNull(month.number)) {
                    null -> Month.JANUARY.startOfMonth(
                        years.nextOrNull(year) ?: return null,
                    )

                    else -> nextMonth.toMonth().startOfMonth(year)
                }

                !day.matches(date) -> day.nextDate(date)?.atTime(0, 0) ?: return null
                hour !in hours -> nextHourCandidate(date, hour) ?: return null
                minute !in minutes -> nextMinuteCandidate(date, hour, minute) ?: return null
                second !in seconds -> when (val nextSecond = seconds.nextOrNull(second)) {
                    null -> nextMinuteCandidate(date, hour, minute) ?: return null
                    else -> date.atTime(hour, minute, nextSecond)
                }

                else -> return candidate
            }
        }
    }

    /**
     * Returns the first matching instant strictly after [from] in [timeZone], or `null` when no
     * match is found within [MAX_SEARCH_YEARS] years of [from] or within the supported date range.
     *
     * Wall-clock times skipped by a daylight-saving transition do not fire. Ambiguous
     * wall-clock times fire once at the earlier offset.
     */
    public fun next(from: Instant = Clock.System.now(), timeZone: TimeZone = TimeZone.UTC): Instant? {
        var local = from.toLocalDateTime(timeZone)
        while (true) {
            val candidate = next(local) ?: return null
            val instant = candidate.toInstant(timeZone)
            val roundTrip = instant.toLocalDateTime(timeZone)

            if (roundTrip == candidate && instant > from) {
                return instant
            }

            local = candidate
        }
    }

    override fun toString(): String = expression

    public companion object {
        /**
         * Builds a [Cron] using the cron DSL.
         */
        public operator fun invoke(block: CronBuilder.() -> Unit): Cron =
            CronBuilder().apply(block).build()

        /**
         * Maximum number of years beyond the initial date that [next] searches before returning
         * `null`.
         */
        internal const val MAX_SEARCH_YEARS = 10

        internal val whitespace: Regex = Regex("\\s+")

        /**
         * Parses a 7-field modern cron expression.
         *
         * Expected format: `second minute hour day-of-month month day-of-week year`
         *
         * @throws CronParseException when the expression is not valid.
         */
        public fun parse(expression: String): Cron {
            try {
                val fields = expression.trim().split(whitespace)
                return when (fields.size) {
                    7 -> Cron(
                        seconds = CronField.Seconds.parse(fields[0]),
                        minutes = CronField.Minutes.parse(fields[1]),
                        hours = CronField.Hours.parse(fields[2]),
                        day = DaySelector.parse(fields[3], fields[5]),
                        months = CronField.Months.parse(fields[4]),
                        years = CronField.Years.parse(fields[6]),
                    )

                    else -> throw CronParseException(
                        "Invalid cron expression '$expression': expected 7 fields " +
                                "(seconds minutes hours day-of-month month day-of-week year) " +
                                "but got ${fields.size}",
                    )
                }
            } catch (e: CronParseException) {
                throw e
            } catch (e: IllegalArgumentException) {
                throw CronParseException("Invalid cron expression '$expression': ${e.message}", e)
            }
        }
    }
}
