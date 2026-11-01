package dev.stashy.worker

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runtime settings for [Worker].
 */
public class WorkerConfig internal constructor() {
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

    /**
     * How long [Worker.stop] waits for executing jobs before cancelling and releasing them.
     */
    public var shutdownGracePeriod: Duration = 30.seconds

    internal var defaults: JobsConfig = JobsConfig()

    /**
     * Sets the defaults every `jobs` block starts with.
     */
    public fun defaults(configure: JobsConfig.() -> Unit) {
        defaults = defaults.copy().apply(configure)
    }

    internal fun validate() {
        require(shutdownGracePeriod.isFinite() && shutdownGracePeriod >= Duration.ZERO) {
            "shutdownGracePeriod must be a non-negative finite duration"
        }
    }
}
