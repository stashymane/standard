package dev.stashy.worker.schedule

import dev.stashy.standard.cron.Cron
import dev.stashy.worker.JobsDsl
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Builds [Schedule] values.
 */
@JobsDsl
public interface ScheduleDsl {
    public companion object : ScheduleDsl

    /**
     * Runs once at [at].
     */
    public fun once(at: Instant = Clock.System.now()): Schedule = Schedule.Once(at)

    /**
     * Runs repeatedly at a fixed interval, starting at [startAt] or one interval after creation.
     */
    public fun every(interval: Duration, startAt: Instant? = null): Schedule =
        Schedule.FixedRate(interval, startAt)

    /**
     * Runs repeatedly, waiting [interval] after each completion.
     */
    public fun after(interval: Duration): Schedule = Schedule.FixedDelay(interval)

    /**
     * Runs according to a modern seven-field cron expression in [timeZone].
     */
    public fun cron(
        expression: String,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Schedule = Schedule.Cron(expression, timeZone.id)

    /**
     * Runs according to a parsed [expression] in [timeZone].
     */
    public fun cron(
        expression: Cron,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Schedule = Schedule.Cron(expression, timeZone.id)

    /**
     * Switches between schedules over time, where `until` closes a segment and `then` opens the
     * last one.
     */
    public fun phased(configure: PhasedScheduleBuilder.() -> Unit): Schedule =
        PhasedScheduleBuilder().apply(configure).build()
}
