package dev.stashy.worker.schedule

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

/**
 * Computes the first occurrence of this schedule relative to [now].
 */
public fun Schedule.firstOccurrence(now: Instant): Instant = when (this) {
    is Schedule.Once -> at
    is Schedule.FixedRate -> startAt ?: (now + interval)
    is Schedule.FixedDelay -> now + interval
    is Schedule.Cron -> expression.next(now, timeZone)
        ?: error("Cron expression '$expression' has no upcoming run")
}

/**
 * Computes the following occurrence after [finishedAt], or null for one-shot schedules.
 *
 * [scheduledAt] is the occurrence that just finished; it preserves fixed-rate cadence.
 */
public fun Schedule.nextOccurrence(scheduledAt: Instant, finishedAt: Instant): Instant? = when (this) {
    is Schedule.Once -> null
    is Schedule.FixedRate -> nextFixedRate(scheduledAt, finishedAt)
    is Schedule.FixedDelay -> finishedAt + interval
    is Schedule.Cron -> expression.next(finishedAt, timeZone)
}

private fun Schedule.FixedRate.nextFixedRate(scheduledAt: Instant, finishedAt: Instant): Instant {
    val elapsed = finishedAt - scheduledAt
    if (elapsed < Duration.ZERO) return scheduledAt + interval

    val intervalNanos = interval.inWholeNanoseconds
    val steps = elapsed.inWholeNanoseconds / intervalNanos + 1
    return scheduledAt + (intervalNanos * steps).nanoseconds
}
