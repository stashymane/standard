package dev.stashy.standard.cron.util

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

internal fun cronDayToIso(value: Int): Int = if (value == 1) 7 else value - 1

internal fun isoDayToCron(value: Int): Int = if (value == 7) 1 else value + 1

internal fun cronDay(value: Int): DayOfWeek = DayOfWeek(cronDayToIso(value))

internal fun DayOfWeek.cronNumber(): Int = isoDayToCron(isoDayNumber)
