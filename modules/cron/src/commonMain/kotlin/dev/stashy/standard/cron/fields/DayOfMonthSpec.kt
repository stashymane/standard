package dev.stashy.standard.cron.fields

import dev.stashy.standard.cron.CronParseException
import dev.stashy.standard.cron.util.firstOfNextMonth
import dev.stashy.standard.cron.util.lastDay
import dev.stashy.standard.cron.util.lastWeekday
import dev.stashy.standard.cron.util.nearestWeekday
import kotlinx.datetime.LocalDate
import kotlin.jvm.JvmInline

internal sealed interface DayOfMonthSpec {
    fun toDayOfMonth(): String
    fun matches(date: LocalDate): Boolean

    /**
     * Returns the first date strictly after [after] matching this spec, the first day of the
     * following month when no date in the current month matches, or `null` past the supported date
     * range.
     */
    fun nextDate(after: LocalDate): LocalDate?

    @JvmInline
    value class Values(val days: CronField.DaysOfMonth) : DayOfMonthSpec {
        override fun toDayOfMonth(): String = days.toExpression()
        override fun matches(date: LocalDate): Boolean = date.day in days

        override fun nextDate(after: LocalDate): LocalDate? {
            val next = days.nextOrNull(after.day)
            return if (next != null && next <= after.month.lastDay(after.year)) {
                LocalDate(after.year, after.month, next)
            } else {
                after.firstOfNextMonth()
            }
        }
    }

    data object Last : DayOfMonthSpec {
        override fun toDayOfMonth(): String = "L"
        override fun matches(date: LocalDate): Boolean =
            date.day == date.month.lastDay(date.year)

        override fun nextDate(after: LocalDate): LocalDate? {
            val last = LocalDate(after.year, after.month, after.month.lastDay(after.year))
            return if (last > after) last else after.firstOfNextMonth()
        }
    }

    data object LastWeekday : DayOfMonthSpec {
        override fun toDayOfMonth(): String = "LW"
        override fun matches(date: LocalDate): Boolean =
            date == date.month.lastWeekday(date.year)

        override fun nextDate(after: LocalDate): LocalDate? {
            val target = after.month.lastWeekday(after.year)
            return if (target > after) target else after.firstOfNextMonth()
        }
    }

    @JvmInline
    value class LastOffset(val offset: Int) : DayOfMonthSpec {
        init {
            require(offset in 1..30) { "Last-day offset must be in 1..30" }
        }

        override fun toDayOfMonth(): String = "L-$offset"
        override fun matches(date: LocalDate): Boolean {
            val target = date.month.lastDay(date.year) - offset
            return target >= 1 && date.day == target
        }

        override fun nextDate(after: LocalDate): LocalDate? {
            val target = after.month.lastDay(after.year) - offset
            return if (target >= 1 && target > after.day) {
                LocalDate(after.year, after.month, target)
            } else {
                after.firstOfNextMonth()
            }
        }
    }

    @JvmInline
    value class NearestWeekday(val day: Int) : DayOfMonthSpec {
        init {
            require(day in CronField.DaysOfMonth.BOUNDS) {
                "Nearest-weekday value must be in 1..31"
            }
        }

        override fun toDayOfMonth(): String = "${day}W"
        override fun matches(date: LocalDate): Boolean =
            date == date.month.nearestWeekday(date.year, day)

        override fun nextDate(after: LocalDate): LocalDate? {
            val target = after.month.nearestWeekday(after.year, day)
            return if (target != null && target > after) target else after.firstOfNextMonth()
        }
    }

    companion object {
        fun parse(field: String): DayOfMonthSpec? {
            return when (val normalized = field.uppercase()) {
                "*", "?" -> null
                "L" -> Last
                "LW" -> LastWeekday

                else if normalized.contains(',') -> {
                    if ('L' in normalized || 'W' in normalized) {
                        throw CronParseException(
                            "Day-of-month specials cannot be combined with a list: $field",
                        )
                    }
                    valuesOrNull(normalized)
                }

                else if normalized.startsWith("L-") -> {
                    val offset = normalized.removePrefix("L-").toIntOrNull()
                        ?: throw CronParseException("Invalid last-day offset: $field")
                    if (offset !in 1..30) {
                        throw CronParseException("Last-day offset must be in 1..30: $field")
                    }
                    LastOffset(offset)
                }

                else if normalized.endsWith('W') -> {
                    val day = normalized.removeSuffix("W").toIntOrNull()
                        ?: throw CronParseException("Invalid nearest-weekday value: $field")
                    if (day !in CronField.DaysOfMonth.BOUNDS) {
                        throw CronParseException("Nearest-weekday value must be in 1..31: $field")
                    }
                    NearestWeekday(day)
                }

                else if ('L' in normalized || 'W' in normalized) -> {
                    throw CronParseException("Invalid day-of-month value: $field")
                }

                else -> valuesOrNull(normalized)
            }
        }

        private fun valuesOrNull(field: String): DayOfMonthSpec? {
            val days = CronField.DaysOfMonth.parse(field)
            return if (days.isFull) null else Values(days)
        }
    }
}
