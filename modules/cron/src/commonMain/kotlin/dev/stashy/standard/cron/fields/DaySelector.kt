package dev.stashy.standard.cron.fields

import dev.stashy.standard.cron.CronParseException
import dev.stashy.standard.cron.util.plusDaysOrNull
import kotlinx.datetime.LocalDate
import kotlin.jvm.JvmInline

internal sealed interface DaySelector {
    fun toDayOfMonth(): String = "?"
    fun toDayOfWeek(): String = "*"
    fun matches(date: LocalDate): Boolean

    /**
     * Returns the first date strictly after [after] matching this selector, the first day of the
     * following month when no date in the current month matches, or `null` past the supported date
     * range.
     */
    fun nextDate(after: LocalDate): LocalDate?

    data object Any : DaySelector {
        override fun matches(date: LocalDate): Boolean = true
        override fun nextDate(after: LocalDate): LocalDate? = after.plusDaysOrNull(1)
    }

    @JvmInline
    value class OfMonth(val spec: DayOfMonthSpec) : DaySelector {
        override fun toDayOfMonth(): String = spec.toDayOfMonth()
        override fun toDayOfWeek(): String = "?"
        override fun matches(date: LocalDate): Boolean = spec.matches(date)
        override fun nextDate(after: LocalDate): LocalDate? = spec.nextDate(after)
    }

    @JvmInline
    value class OfWeek(val spec: DayOfWeekSpec) : DaySelector {
        override fun toDayOfWeek(): String = spec.toDayOfWeek()
        override fun matches(date: LocalDate): Boolean = spec.matches(date)
        override fun nextDate(after: LocalDate): LocalDate? = spec.nextDate(after)
    }

    companion object {
        fun parse(dayOfMonth: String, dayOfWeek: String): DaySelector {
            val dom = DayOfMonthSpec.parse(dayOfMonth)
            val dow = DayOfWeekSpec.parse(dayOfWeek)

            return when {
                dom != null && dow != null -> throw CronParseException(
                    "Day-of-month and day-of-week cannot both be restricted; use '?' for one of them",
                )

                dom != null -> OfMonth(dom)
                dow != null -> OfWeek(dow)
                else -> Any
            }
        }
    }
}
