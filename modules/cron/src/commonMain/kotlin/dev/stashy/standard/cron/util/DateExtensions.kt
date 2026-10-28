package dev.stashy.standard.cron.util

import dev.stashy.standard.cron.Cron
import dev.stashy.standard.cron.fields.CronField
import kotlinx.datetime.*

internal fun Month.lastDay(year: Int): Int = when (this) {
    Month.FEBRUARY -> if (year.isLeapYear()) 29 else 28
    Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30
    else -> 31
}

private fun Int.isLeapYear(): Boolean =
    this % 4 == 0 && (this % 100 != 0 || this % 400 == 0)

internal fun Month.lastWeekday(year: Int): LocalDate {
    val last = LocalDate(year, this, lastDay(year))
    return when (last.dayOfWeek) {
        DayOfWeek.SATURDAY -> last.minus(1, DAY)
        DayOfWeek.SUNDAY -> last.minus(2, DAY)
        else -> last
    }
}

internal fun Month.nearestWeekday(year: Int, day: Int): LocalDate? {
    val last = lastDay(year)
    if (day !in 1..last) return null

    val date = LocalDate(year, this, day)
    return when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> when (day) {
            1 -> date.plus(2, DAY)
            else -> date.minus(1, DAY)
        }

        DayOfWeek.SUNDAY -> when (day) {
            last -> date.minus(2, DAY)
            else -> date.plus(1, DAY)
        }

        else -> date
    }
}

internal fun Month.startOfMonth(year: Int): LocalDateTime =
    LocalDate(year, this, 1).atTime(0, 0)

internal fun LocalDate.plusDaysOrNull(days: Int): LocalDate? {
    require(days >= 0) { "Days must not be negative" }
    if (year == CronField.Years.MAX_YEAR &&
        month == Month.DECEMBER &&
        day + days > month.lastDay(year)
    ) {
        return null
    }
    return plus(days, DAY)
}

internal fun LocalDate.firstOfNextMonth(): LocalDate? {
    if (year == CronField.Years.MAX_YEAR && month == Month.DECEMBER) return null
    return LocalDate(year, month, 1).plus(1, MONTH)
}

internal fun LocalDate.nextDayStartOrNull(): LocalDateTime? =
    plusDaysOrNull(1)?.atTime(0, 0)

internal fun Cron.nextHourCandidate(date: LocalDate, hour: Int): LocalDateTime? =
    when (val nextHour = hours.nextOrNull(hour)) {
        null -> date.nextDayStartOrNull()
        else -> date.atTime(nextHour, 0)
    }

internal fun Cron.nextMinuteCandidate(
    date: LocalDate,
    hour: Int,
    minute: Int,
): LocalDateTime? =
    when (val nextMinute = minutes.nextOrNull(minute)) {
        null -> nextHourCandidate(date, hour)
        else -> date.atTime(hour, nextMinute)
    }

internal fun LocalDateTime.plusOneSecond(): LocalDateTime? {
    val nextSecondOfDay = hour * 3600 + minute * 60 + second + 1
    return when {
        nextSecondOfDay < 86_400 -> date.atTime(LocalTime.fromSecondOfDay(nextSecondOfDay))
        else -> date.nextDayStartOrNull()
    }
}

internal fun Int.toMonth(): Month = Month.entries[this - 1]
