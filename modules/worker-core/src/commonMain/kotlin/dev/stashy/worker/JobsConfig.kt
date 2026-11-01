package dev.stashy.worker

import dev.stashy.worker.job.RetryPolicy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Runtime settings for one `jobs` block.
 */
public data class JobsConfig(
    /**
     * How often the block polls for jobs that are ready to be claimed.
     */
    public var pollInterval: Duration = Defaults.pollInterval,

    /**
     * How early a job may be claimed and prepared before its scheduled instant.
     *
     * This must be at least as long as [pollInterval] and should cover the time a handler needs to
     * prepare.
     */
    public var prepareWindow: Duration = Defaults.prepareWindow,

    /**
     * How long a claimed job remains owned by this worker before another worker may recover it.
     */
    public var leaseDuration: Duration = Defaults.leaseDuration,

    /**
     * How often an active job's lease is renewed. Must be shorter than [leaseDuration].
     */
    public var heartbeatInterval: Duration = Defaults.heartbeatInterval,

    /**
     * Maximum claimed jobs in this block, including prepared, waiting, and executing jobs.
     */
    public var maxConcurrentJobs: Int = Defaults.maxConcurrentJobs,

    /**
     * Default retry policy for jobs defined through this block, unless overridden by the job builder.
     */
    public var retry: RetryPolicy = Defaults.retry
) {
    @Suppress("ConstPropertyName")
    public object Defaults {
        public val pollInterval: Duration = 5.seconds
        public val prepareWindow: Duration = 5.seconds
        public val leaseDuration: Duration = 1.minutes
        public val heartbeatInterval: Duration = 20.seconds
        public const val maxConcurrentJobs: Int = 8
        public val retry: RetryPolicy = RetryPolicy()
    }

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
    }
}
