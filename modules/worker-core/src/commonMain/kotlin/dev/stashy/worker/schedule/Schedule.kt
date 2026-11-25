package dev.stashy.worker.schedule

import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant
import dev.stashy.standard.cron.Cron as CronExpression

/**
 * When a job should run.
 */
@Serializable
public sealed interface Schedule {
    public fun firstOccurrence(now: Instant): Instant
    public fun nextOccurrence(scheduledAt: Instant, finishedAt: Instant): Instant?

    /**
     * Runs once at [at].
     */
    @Serializable
    @SerialName("once")
    public data class Once(
        public val at: Instant,
    ) : Schedule {
        override fun firstOccurrence(now: Instant): Instant = at
        override fun nextOccurrence(scheduledAt: Instant, finishedAt: Instant): Instant? = null
    }

    /**
     * Runs repeatedly at a fixed interval. The first run is at [startAt], or one interval after the
     * job is created when [startAt] is null.
     */
    @Serializable
    @SerialName("fixed-rate")
    public data class FixedRate(
        public val interval: Duration,
        public val startAt: Instant? = null,
    ) : Schedule {
        init {
            require(interval.isFinite() && interval > Duration.ZERO) {
                "interval must be a positive finite duration"
            }
        }

        override fun firstOccurrence(now: Instant): Instant = startAt ?: (now + interval)

        override fun nextOccurrence(scheduledAt: Instant, finishedAt: Instant): Instant {
            val elapsed = finishedAt - scheduledAt
            if (elapsed < Duration.ZERO) return scheduledAt + interval

            val intervalNanos = interval.inWholeNanoseconds
            val steps = elapsed.inWholeNanoseconds / intervalNanos + 1
            return scheduledAt + (intervalNanos * steps).nanoseconds
        }
    }

    /**
     * Runs repeatedly, waiting [interval] after each completion.
     */
    @Serializable
    @SerialName("fixed-delay")
    public data class FixedDelay(public val interval: Duration) : Schedule {
        init {
            require(interval.isFinite() && interval > Duration.ZERO) {
                "interval must be a positive finite duration"
            }
        }

        override fun firstOccurrence(now: Instant): Instant = now + interval
        override fun nextOccurrence(scheduledAt: Instant, finishedAt: Instant): Instant = finishedAt + interval
    }

    /**
     * Runs according to a modern seven-field cron expression in [timeZoneId].
     */
    @Serializable
    @SerialName("cron")
    public data class Cron(
        public val expression: CronExpression,
        public val timeZoneId: String = TimeZone.currentSystemDefault().id,
    ) : Schedule {
        public constructor(
            expression: String,
            timeZoneId: String = TimeZone.currentSystemDefault().id,
        ) : this(CronExpression.parse(expression), timeZoneId)

        init {
            TimeZone.of(timeZoneId) //validation - throws if timezone ID is invalid
        }

        public val timeZone: TimeZone
            get() = TimeZone.of(timeZoneId)

        override fun firstOccurrence(now: Instant): Instant =
            expression.next(now, timeZone) ?: error("Cron expression '$expression' has no upcoming run")

        override fun nextOccurrence(scheduledAt: Instant, finishedAt: Instant): Instant? =
            expression.next(finishedAt, timeZone)
    }
}
