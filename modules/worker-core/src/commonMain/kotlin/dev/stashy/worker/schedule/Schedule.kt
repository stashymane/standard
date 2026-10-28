package dev.stashy.worker.schedule

import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant
import dev.stashy.standard.cron.Cron as CronExpression

/**
 * When a job should run.
 */
@Serializable
public sealed interface Schedule {
    /**
     * Runs once at [at]. This is the exact-instant schedule.
     */
    @Serializable
    @SerialName("once")
    public data class Once(
        public val at: Instant,
    ) : Schedule

    /**
     * Runs repeatedly at a fixed interval. The first run is at [startAt], or one interval after the
     * job is created when [startAt] is null. Missed occurrences are skipped.
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
            TimeZone.of(timeZoneId)
        }

        public val timeZone: TimeZone
            get() = TimeZone.of(timeZoneId)
    }
}
