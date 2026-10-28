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
    public var pollInterval: Duration = 5.seconds
    public var prepareWindow: Duration = 30.seconds
    public var leaseDuration: Duration = 1.minutes
    public var heartbeatInterval: Duration = 20.seconds
    public var maxConcurrentJobs: Int = 8
    public var shutdownGracePeriod: Duration = 30.seconds
    public var retry: RetryPolicy = RetryPolicy()
    public var clock: Clock = Clock.System
    public var dispatcher: CoroutineDispatcher = Dispatchers.Default
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
