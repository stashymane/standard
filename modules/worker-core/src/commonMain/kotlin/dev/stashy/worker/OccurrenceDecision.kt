package dev.stashy.worker

import dev.stashy.worker.job.Job
import dev.stashy.worker.job.JobQueue
import dev.stashy.worker.job.LateExecutionPolicy
import dev.stashy.worker.schedule.Schedule
import dev.stashy.worker.schedule.nextOccurrence
import kotlin.time.Instant

/**
 * The decided fate of a claimed job once an occurrence resolves.
 *
 * All transitions are decided here, so that retry, late-execution, and recurring-schedule rules
 * stay consistent across execution, lease recovery, and shutdown.
 */
internal sealed interface OccurrenceDecision {
    /**
     * Recorded in job history; null on a clean completion.
     */
    val error: String?

    /**
     * The occurrence finished; [nextRunAt] schedules the next one, or completes a one-shot job.
     */
    data class Complete(val nextRunAt: Instant?, override val error: String? = null) : OccurrenceDecision

    /**
     * The occurrence is released for another attempt at [retryAt], keeping its attempt count.
     */
    data class Retry(val retryAt: Instant, override val error: String) : OccurrenceDecision

    /**
     * The job is permanently failed.
     */
    data class Fail(override val error: String) : OccurrenceDecision

    companion object {
        /**
         * Decision for a successful occurrence: complete it, scheduling the next one when recurring.
         */
        fun success(claim: JobQueue.Claimed, finishedAt: Instant): OccurrenceDecision =
            Complete(claim.job.definition.schedule.nextOccurrence(claim.scheduledAt, finishedAt))

        /**
         * Decision for a failed occurrence: a retry while attempts remain, unless its instant would
         * violate the late-execution policy; the occurrence ends once attempts are exhausted.
         */
        fun failure(claim: JobQueue.Claimed, now: Instant, error: String): OccurrenceDecision {
            val definition = claim.job.definition
            if (claim.attempt >= definition.retry.maxAttempts) {
                return terminal(claim, now, "Attempts exhausted: $error")
            }
            val retryAt = now + definition.retry.delayFor(claim.attempt)
            if (definition.startsTooLate(retryAt, claim.scheduledAt)) {
                return Fail(
                    "Next attempt would start ${retryAt - claim.scheduledAt} after its scheduled instant: $error",
                )
            }
            return Retry(retryAt, error)
        }

        /**
         * Ends an occurrence without further attempts: one-shot jobs fail, while recurring jobs
         * advance to their next occurrence with [error] recorded.
         */
        fun terminal(claim: JobQueue.Claimed, now: Instant, error: String): OccurrenceDecision {
            val next = claim.job.definition.schedule.nextOccurrence(claim.scheduledAt, now)
            return if (next == null) Fail(error) else Complete(next, error)
        }
    }
}

/**
 * Whether an occurrence of this job starting at [startAt] violates its late-execution policy.
 */
internal fun Job.Definition.startsTooLate(startAt: Instant, scheduledAt: Instant): Boolean {
    if (schedule !is Schedule.Once) return false
    val policy = latePolicy as? LateExecutionPolicy.FailAfter ?: return false
    return startAt - scheduledAt > policy.tolerance
}
