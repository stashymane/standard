package dev.stashy.standard.cron.fields

import dev.stashy.standard.cron.CronParseException
import dev.stashy.standard.cron.util.cronDay
import dev.stashy.standard.cron.util.cronNumber
import dev.stashy.standard.cron.util.firstOfNextMonth
import dev.stashy.standard.cron.util.lastDay
import dev.stashy.standard.cron.util.plusDaysOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlin.jvm.JvmInline

internal sealed interface DayOfWeekSpec {
    fun toDayOfWeek(): String
    fun matches(date: LocalDate): Boolean

    /**
     * Returns the first date strictly after [after] matching this spec, the first day of the
     * following month when no date in the current month matches, or `null` past the supported date
     * range.
     */
    fun nextDate(after: LocalDate): LocalDate?

    @JvmInline
    value class Values(val days: CronField.DaysOfWeek) : DayOfWeekSpec {
        override fun toDayOfWeek(): String = days.toExpression()
        override fun matches(date: LocalDate): Boolean =
            date.dayOfWeek.cronNumber() in days

        override fun nextDate(after: LocalDate): LocalDate? {
            val current = after.dayOfWeek.cronNumber()
            val offset = (1..7).first { day ->
                (current - 1 + day) % 7 + 1 in days
            }
            return after.plusDaysOrNull(offset)
        }
    }

    @JvmInline
    value class Last(val dayOfWeek: DayOfWeek) : DayOfWeekSpec {
        override fun toDayOfWeek(): String =
            "${CronField.DaysOfWeek.labelFor(dayOfWeek.cronNumber())}L"

        override fun matches(date: LocalDate): Boolean =
            date.dayOfWeek == dayOfWeek && date.day + 7 > date.month.lastDay(date.year)

        override fun nextDate(after: LocalDate): LocalDate? {
            val last = LocalDate(after.year, after.month, after.month.lastDay(after.year))
            val offset = (last.dayOfWeek.isoDayNumber - dayOfWeek.isoDayNumber + 7) % 7
            val target = last.minus(offset, DateTimeUnit.DAY)
            return if (target > after) target else after.firstOfNextMonth()
        }
    }

    data class Nth(
        val nth: Int,
        val dayOfWeek: DayOfWeek,
    ) : DayOfWeekSpec {
        init {
            require(nth in 1..5) { "Day-of-week occurrence must be in 1..5" }
        }

        override fun toDayOfWeek(): String =
            "${CronField.DaysOfWeek.labelFor(dayOfWeek.cronNumber())}#$nth"

        override fun matches(date: LocalDate): Boolean =
            date.dayOfWeek == dayOfWeek && (date.day - 1) / 7 + 1 == nth

        override fun nextDate(after: LocalDate): LocalDate? {
            val first = LocalDate(after.year, after.month, 1)
            val offset = (dayOfWeek.isoDayNumber - first.dayOfWeek.isoDayNumber + 7) % 7
            val target = first.plusDaysOrNull(offset + 7 * (nth - 1)) ?: return null
            return if (target.month == after.month && target > after) {
                target
            } else {
                after.firstOfNextMonth()
            }
        }
    }

    companion object {
        fun parse(field: String): DayOfWeekSpec? {
            return when (val normalized = field.uppercase()) {
                "*", "?" -> null

                else if normalized.contains(',') -> {
                    if ('L' in normalized || '#' in normalized) {
                        throw CronParseException("Day-of-week specials cannot be combined with a list: $field")
                    }
                    valuesOrNull(normalized)
                }

                else if '#' in normalized -> {
                    val parts = normalized.split('#')
                    if (parts.size != 2) {
                        throw CronParseException("Invalid day-of-week occurrence: $field")
                    }
                    val day = parseDayValue(parts[0])
                    val nth = parts[1].toIntOrNull()
                        ?: throw CronParseException("Invalid occurrence number: $field")
                    if (nth !in 1..5) {
                        throw CronParseException("Day-of-week occurrence must be in 1..5: $field")
                    }
                    Nth(nth, cronDay(day))
                }

                else if normalized.endsWith('L') -> {
                    val token = normalized.dropLast(1)
                    if (token.isEmpty()) {
                        throw CronParseException("Day-of-week 'L' requires a weekday: $field")
                    }
                    Last(cronDay(parseDayValue(token)))
                }

                else if ('L' in normalized || '#' in normalized) -> {
                    throw CronParseException("Invalid day-of-week value: $field")
                }

                else -> valuesOrNull(normalized)
            }
        }

        private fun valuesOrNull(field: String): DayOfWeekSpec? {
            val days = CronField.DaysOfWeek.parse(field)
            return if (days.isFull) null else Values(days)
        }

        private fun parseDayValue(token: String): Int =
            CronField.DaysOfWeek.parseValue(token)
    }
}
