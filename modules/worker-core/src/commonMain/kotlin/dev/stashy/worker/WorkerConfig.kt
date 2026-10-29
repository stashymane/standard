package dev.stashy.worker

import dev.stashy.worker.job.RetryPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Runtime settings for [Worker].
 */
public class WorkerConfig internal constructor() {
    /**
     * How often the worker polls for jobs that are ready to be claimed.
     */
    public var pollInterval: Duration = 5.seconds

    /**
     * How early a job may be claimed and prepared before its scheduled instant.
     *
     * This must be at least as long as [pollInterval] and should cover the time a prepared handler needs.
     */
    public var prepareWindow: Duration = 30.seconds

    /**
     * How long a claimed job remains owned by this worker before another worker may recover it.
     */
    public var leaseDuration: Duration = 1.minutes

    /**
     * How often an active job's lease is renewed. Must be shorter than [leaseDuration].
     */
    public var heartbeatInterval: Duration = 20.seconds

    /**
     * Maximum claimed jobs, including prepared, waiting, and executing jobs.
     */
    public var maxConcurrentJobs: Int = 8

    /**
     * How long [Worker.stop] waits for executing jobs before cancelling and releasing them.
     */
    public var shutdownGracePeriod: Duration = 30.seconds

    /**
     * Default retry policy for newly defined jobs, unless overridden by the job builder.
     */
    public var retry: RetryPolicy = RetryPolicy()

    /**
     * Clock used for scheduling, leases, retries, and handler contexts.
     */
    public var clock: Clock = Clock.System

    /**
     * Dispatcher used for polling, preparation, and execution.
     */
    public var dispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * Receives worker diagnostics and failures.
     */
    public var logger: WorkerLogger = WorkerLogger.None

    internal fun validate() {
        require(pollInterval.isFinite() && pollInterval > Duration.ZERO) {
            "pollInterval must be a positive finite duration"
        }
        require(prepareWindow.isFinite() && prepareWindow >= pollInterval) {
            "prepareWindow must be finite and at least pollInterval"
        }
        require(leaseDuration.isFinite() && leaseDuration > Duration.ZERO) {
            "leaseDuration must be a positive finite duration"
        }
        require(heartbeatInterval.isFinite() && heartbeatInterval > Duration.ZERO) {
            "heartbeatInterval must be a positive finite duration"
        }
        require(heartbeatInterval < leaseDuration) {
            "heartbeatInterval must be shorter than leaseDuration"
        }
        require(maxConcurrentJobs > 0) { "maxConcurrentJobs must be positive" }
        require(shutdownGracePeriod.isFinite() && shutdownGracePeriod >= Duration.ZERO) {
            "shutdownGracePeriod must be a non-negative finite duration"
        }
    }
}
