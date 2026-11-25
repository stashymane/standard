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
    /**
     * @return the first occurrence of this schedule starting from [now].
     */
    public fun firstOccurrence(now: Instant): Instant

    /**
     * @return the next occurrence of this schedule, based on when the job was [scheduledAt] and the latest occurrence [finishedAt].
     */
    public fun nextOccurrence(scheduledAt: Instant, finishedAt: Instant): Instant?

    /**
     * The innermost schedule that applies at [instant].
     */
    public fun activeAt(instant: Instant): Schedule = this

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

    /**
     * Switches between [phases] over time.
     * Each phase applies from its [Phase.from] instant until the next phase starts (exclusively).
     * The first phase is unbounded, so [phases] covers every instant.
     */
    @Serializable
    @SerialName("phased")
    public data class Phased(public val phases: List<Phase>) : Schedule {
        @Serializable
        public data class Phase(
            public val from: Instant?,
            public val schedule: Schedule,
        )

        init {
            require(phases.isNotEmpty()) { "phases must not be empty" }
            require(phases.first().from == null) { "the first phase must be unbounded" }
            val starts = phases.drop(1).map(Phase::from)
            require(starts.all { it != null }) { "only the first phase may be unbounded" }
            require(starts.filterNotNull().zipWithNext().all { (previous, next) -> previous < next }) {
                "phase start instants must increase"
            }
        }

        override fun firstOccurrence(now: Instant): Instant =
            resolve(now) { phase -> phase.firstOccurrence(now) }
                ?: error("Phased schedule has no upcoming run")

        override fun nextOccurrence(scheduledAt: Instant, finishedAt: Instant): Instant? {
            val initial: (Schedule) -> Instant? =
                if (indexAt(scheduledAt) == indexAt(finishedAt)) {
                    { phase -> phase.nextOccurrence(scheduledAt, finishedAt) }
                } else {
                    { phase -> phase.firstOccurrence(finishedAt) }
                }
            return resolve(finishedAt, initial)
        }

        override fun activeAt(instant: Instant): Schedule =
            phases[indexAt(instant)].schedule.activeAt(instant)

        private fun indexAt(instant: Instant): Int =
            phases.indexOfLast { it.from == null || it.from <= instant }

        private fun resolve(from: Instant, initial: (Schedule) -> Instant?): Instant? {
            var index = indexAt(from)
            var next = initial(phases[index].schedule)
            while (true) {
                val boundary = phases.getOrNull(index + 1)?.from
                if (next != null && (boundary == null || next < boundary)) return next
                if (boundary == null) return null
                index++
                next = phases[index].schedule.firstOccurrence(boundary)
            }
        }
    }
}
