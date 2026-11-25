package dev.stashy.worker.job

import dev.stashy.worker.JobsDsl
import dev.stashy.worker.schedule.Schedule
import dev.stashy.worker.schedule.ScheduleDsl

/**
 * Builds a [Job] for a registered handler.
 */
@JobsDsl
public class JobBuilder internal constructor(
    private val handlerId: String,
    private val id: Job.Id,
    defaultRetry: RetryPolicy,
) : ScheduleDsl {
    public var enabled: Boolean = true
    public var retry: RetryPolicy = defaultRetry
    public var latePolicy: LateExecutionPolicy = LateExecutionPolicy.Run

    /**
     * When the job runs; null until the builder defines one.
     */
    public var schedule: Schedule? = null

    internal fun build(): Job = Job(
        id = id,
        handlerId = handlerId,
        schedule = schedule ?: error("Job \"${id.value}\" has no schedule"),
        enabled = enabled,
        retry = retry,
        latePolicy = latePolicy,
    )
}

/**
 * Creates a job through the [JobBuilder] DSL.
 */
public fun defineJob(
    handlerId: String,
    id: Job.Id,
    defaultRetry: RetryPolicy = RetryPolicy.Exponential(),
    configure: JobBuilder.() -> Unit,
): Job = JobBuilder(handlerId, id, defaultRetry).apply(configure).build()
