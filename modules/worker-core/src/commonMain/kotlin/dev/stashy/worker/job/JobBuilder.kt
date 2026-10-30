package dev.stashy.worker.job

import dev.stashy.standard.cron.Cron
import dev.stashy.worker.schedule.Schedule
import kotlinx.datetime.TimeZone
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Builds a [Job] for a registered handler.
 */
public class JobBuilder internal constructor(
    private val handlerId: String,
    private val id: Job.Id,
    defaultRetry: RetryPolicy,
) {
    public var enabled: Boolean = true
    public var retry: RetryPolicy = defaultRetry
    public var latePolicy: LateExecutionPolicy = LateExecutionPolicy.Run

    private var selectedSchedule: Schedule? = null

    public fun schedule(schedule: Schedule): Unit {
        selectedSchedule = schedule
    }

    public fun once(at: Instant): Unit {
        selectedSchedule = Schedule.Once(at)
    }

    public fun every(interval: Duration, startAt: Instant? = null): Unit {
        selectedSchedule = Schedule.FixedRate(interval, startAt)
    }

    public fun after(interval: Duration): Unit {
        selectedSchedule = Schedule.FixedDelay(interval)
    }

    public fun cron(expression: String, timeZone: TimeZone = TimeZone.currentSystemDefault()): Unit {
        selectedSchedule = Schedule.Cron(expression, timeZone.id)
    }

    public fun cron(expression: Cron, timeZone: TimeZone = TimeZone.currentSystemDefault()): Unit {
        selectedSchedule = Schedule.Cron(expression, timeZone.id)
    }

    internal fun build(): Job {
        val schedule = selectedSchedule ?: error("Job '${id.value}' has no schedule")
        return Job(
            id = id,
            handlerId = handlerId,
            schedule = schedule,
            enabled = enabled,
            retry = retry,
            latePolicy = latePolicy,
        )
    }
}

/**
 * Creates a job through the [JobBuilder] DSL.
 */
public fun defineJob(
    handlerId: String,
    id: Job.Id,
    defaultRetry: RetryPolicy = RetryPolicy(),
    configure: JobBuilder.() -> Unit,
): Job = JobBuilder(handlerId, id, defaultRetry).apply(configure).build()
