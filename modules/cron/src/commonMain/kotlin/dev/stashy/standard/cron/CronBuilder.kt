package dev.stashy.standard.cron

import dev.stashy.standard.cron.fields.*
import dev.stashy.standard.cron.util.cronNumber
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import kotlinx.datetime.number

/**
 * DSL entry point for building cron expressions.
 */
public class CronBuilder internal constructor() {
    private val seconds = FieldSpec(CronField.Seconds.BOUNDS, default = listOf(Segment(0, 0))) {
        CronField.Seconds(it)
    }
    private val minutes = FieldSpec(CronField.Minutes.BOUNDS) { CronField.Minutes(it) }
    private val hours = FieldSpec(CronField.Hours.BOUNDS) { CronField.Hours(it) }
    private val months = FieldSpec(CronField.Months.BOUNDS) { CronField.Months(it) }
    private val years = FieldSpec(CronField.Years.BOUNDS) { CronField.Years(it) }
    private val daysOfMonth = DayOfMonthSpecBuilder()
    private val daysOfWeek = DayOfWeekSpecBuilder()

    public fun seconds(block: TimeFieldBuilder.() -> Unit): Unit {
        TimeFieldBuilder(seconds).block()
    }

    public fun minutes(block: TimeFieldBuilder.() -> Unit): Unit {
        TimeFieldBuilder(minutes).block()
    }

    public fun hours(block: TimeFieldBuilder.() -> Unit): Unit {
        TimeFieldBuilder(hours).block()
    }

    public fun daysOfMonth(block: DayOfMonthBuilder.() -> Unit): Unit {
        DayOfMonthBuilder(daysOfMonth).block()
    }

    public fun months(block: MonthBuilder.() -> Unit): Unit {
        MonthBuilder(months).block()
    }

    public fun daysOfWeek(block: DayOfWeekBuilder.() -> Unit): Unit {
        DayOfWeekBuilder(daysOfWeek).block()
    }

    public fun years(block: YearBuilder.() -> Unit): Unit {
        YearBuilder(years).block()
    }

    internal fun build(): Cron {
        val dayOfMonth = daysOfMonth.buildOrNull()
        val dayOfWeek = daysOfWeek.buildOrNull()
        val day = when {
            dayOfMonth != null && dayOfWeek != null -> throw IllegalStateException(
                "Day-of-month and day-of-week cannot both be restricted",
            )

            dayOfMonth != null -> DaySelector.OfMonth(dayOfMonth)
            dayOfWeek != null -> DaySelector.OfWeek(dayOfWeek)
            else -> DaySelector.Any
        }

        return Cron(
            seconds = seconds.snapshot(),
            minutes = minutes.snapshot(),
            hours = hours.snapshot(),
            day = day,
            months = months.snapshot(),
            years = years.snapshot(),
        )
    }
}

/**
 * Builder for seconds, minutes, and hours.
 */
public class TimeFieldBuilder internal constructor(private val spec: FieldSpec<*>) {
    public fun at(vararg values: Int): Unit {
        values.forEach(spec::add)
    }

    public fun between(from: Int, to: Int, step: Int = 1): Unit {
        spec.addRange(from, to, step)
    }

    public fun every(step: Int): Unit {
        spec.addRange(spec.bounds.first, spec.bounds.last, step)
    }
}

/**
 * Builder for the month field.
 */
public class MonthBuilder internal constructor(private val spec: FieldSpec<CronField.Months>) {
    public fun on(vararg months: Month): Unit {
        months.forEach { spec.add(it.number) }
    }

    public fun between(from: Month, to: Month, step: Int = 1): Unit {
        spec.addRange(from.number, to.number, step)
    }

    public fun every(step: Int): Unit {
        spec.addRange(CronField.Months.BOUNDS.first, CronField.Months.BOUNDS.last, step)
    }
}

/**
 * Builder for the year field.
 */
public class YearBuilder internal constructor(private val spec: FieldSpec<CronField.Years>) {
    public fun on(vararg years: Int): Unit {
        years.forEach(spec::add)
    }

    public fun between(from: Int, to: Int, step: Int = 1): Unit {
        spec.addRange(from, to, step)
    }

    public fun every(step: Int): Unit {
        spec.addRange(CronField.Years.BOUNDS.first, CronField.Years.BOUNDS.last, step)
    }
}

/**
 * Builder for the day-of-month field.
 */
public class DayOfMonthBuilder internal constructor(private val spec: DayOfMonthSpecBuilder) {
    public fun on(vararg days: Int): Unit {
        days.forEach(spec::add)
    }

    public fun between(from: Int, to: Int, step: Int = 1): Unit {
        spec.addRange(from, to, step)
    }

    public fun every(step: Int): Unit {
        spec.every(step)
    }

    public fun last(): Unit {
        spec.last()
    }

    public fun last(offset: Int): Unit {
        spec.last(offset)
    }

    public fun lastWeekday(): Unit {
        spec.lastWeekday()
    }

    public fun nearestWeekday(day: Int): Unit {
        spec.nearestWeekday(day)
    }
}

/**
 * Builder for the day-of-week field.
 */
public class DayOfWeekBuilder internal constructor(private val spec: DayOfWeekSpecBuilder) {
    public fun on(vararg days: DayOfWeek): Unit {
        days.forEach(spec::add)
    }

    public fun between(from: DayOfWeek, to: DayOfWeek, step: Int = 1): Unit {
        spec.addRange(from, to, step)
    }

    public fun every(step: Int): Unit {
        spec.every(step)
    }

    public fun last(day: DayOfWeek): Unit {
        spec.last(day)
    }

    public fun nth(nth: Int, day: DayOfWeek): Unit {
        spec.nth(nth, day)
    }
}

internal class DayOfMonthSpecBuilder {
    private val values = FieldSpec(CronField.DaysOfMonth.BOUNDS) { CronField.DaysOfMonth(it) }
    private var special: DayOfMonthSpec? = null

    internal fun add(day: Int) {
        ensureNoSpecial()
        values.add(day)
    }

    internal fun addRange(from: Int, to: Int, step: Int) {
        ensureNoSpecial()
        values.addRange(from, to, step)
    }

    internal fun every(step: Int) {
        ensureNoSpecial()
        values.addRange(CronField.DaysOfMonth.BOUNDS.first, CronField.DaysOfMonth.BOUNDS.last, step)
    }

    internal fun last() {
        setSpecial(DayOfMonthSpec.Last)
    }

    internal fun last(offset: Int) {
        setSpecial(DayOfMonthSpec.LastOffset(offset))
    }

    internal fun lastWeekday() {
        setSpecial(DayOfMonthSpec.LastWeekday)
    }

    internal fun nearestWeekday(day: Int) {
        setSpecial(DayOfMonthSpec.NearestWeekday(day))
    }

    internal fun buildOrNull(): DayOfMonthSpec? {
        special?.let { return it }
        val field = values.snapshotOrNull() ?: return null
        return if (field.isFull) {
            null
        } else {
            DayOfMonthSpec.Values(field)
        }
    }

    private fun setSpecial(value: DayOfMonthSpec) {
        check(special == null) { "A day-of-month special has already been set" }
        check(!values.touched) { "A day-of-month special cannot be combined with other values" }
        special = value
    }

    private fun ensureNoSpecial() {
        check(special == null) { "A day-of-month special cannot be combined with other values" }
    }
}

internal class DayOfWeekSpecBuilder {
    private val values = FieldSpec(CronField.DaysOfWeek.BOUNDS) { CronField.DaysOfWeek(it) }
    private var special: DayOfWeekSpec? = null

    internal fun add(day: DayOfWeek) {
        ensureNoSpecial()
        values.add(day.cronNumber())
    }

    internal fun addRange(from: DayOfWeek, to: DayOfWeek, step: Int) {
        ensureNoSpecial()
        val fromCron = from.cronNumber()
        val toCron = to.cronNumber()
        require(fromCron <= toCron) { "Day-of-week range must be ascending" }
        values.addRange(fromCron, toCron, step)
    }

    internal fun every(step: Int) {
        ensureNoSpecial()
        values.addRange(CronField.DaysOfWeek.BOUNDS.first, CronField.DaysOfWeek.BOUNDS.last, step)
    }

    internal fun last(day: DayOfWeek) {
        setSpecial(DayOfWeekSpec.Last(day))
    }

    internal fun nth(nth: Int, day: DayOfWeek) {
        setSpecial(DayOfWeekSpec.Nth(nth, day))
    }

    internal fun buildOrNull(): DayOfWeekSpec? {
        special?.let { return it }
        val field = values.snapshotOrNull() ?: return null
        return if (field.isFull) {
            null
        } else {
            DayOfWeekSpec.Values(field)
        }
    }

    private fun setSpecial(value: DayOfWeekSpec) {
        check(special == null) { "A day-of-week special has already been set" }
        check(!values.touched) { "A day-of-week special cannot be combined with other values" }
        special = value
    }

    private fun ensureNoSpecial() {
        check(special == null) { "A day-of-week special cannot be combined with other values" }
    }
}
