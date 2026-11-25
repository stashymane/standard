@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.stashy.worker.worker

import dev.stashy.worker.Worker
import dev.stashy.worker.WorkerBuilder
import dev.stashy.worker.handler.JobsRegistry
import dev.stashy.worker.job.RetryPolicy
import dev.stashy.worker.pool.ConcurrencyPool
import dev.stashy.worker.pool.PoolRequest
import dev.stashy.worker.store.JobHistory
import dev.stashy.worker.store.JobQueue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal fun TestScope.scope(): CoroutineScope = CoroutineScope(
    SupervisorJob() +
            StandardTestDispatcher(testScheduler) +
            CoroutineExceptionHandler { _, _ -> },
)

internal fun TestScope.workerWith(
    clock: Clock,
    poll: Duration = 10.seconds,
    configure: WorkerBuilder.() -> Unit,
): Worker = Worker.Companion(id = "worker") {
    configure()
    config {
        shutdownGracePeriod = 1.seconds
        this.clock = clock
        dispatcher = StandardTestDispatcher(testScheduler)
        defaults {
            pollInterval = poll
            prepareWindow = poll
            leaseDuration = 1.minutes
            heartbeatInterval = 20.seconds
            retry = RetryPolicy.Exponential(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0)
        }
    }
}

internal class CountingPool(override val capacity: Int) : ConcurrencyPool {
    override val label: String? = null

    private val permits = Semaphore(capacity)
    var granted = 0
    var released = 0

    override suspend fun admit(request: PoolRequest): Int {
        permits.acquire()
        granted++
        return 1
    }

    override fun release(count: Int) {
        released += count
        repeat(count) { permits.release() }
    }
}

internal fun TestScope.worker(
    clock: Clock,
    store: JobQueue<*>,
    history: JobHistory? = null,
    retry: RetryPolicy = RetryPolicy.Exponential(maxAttempts = 3, initialDelay = 10.seconds, jitter = 0.0),
    lease: Duration = 1.minutes,
    heartbeat: Duration = 20.seconds,
    poll: Duration = 10.seconds,
    prepareWindow: Duration = poll,
    configureHandlers: JobsRegistry<*>.() -> Unit,
): Worker = Worker.Companion(id = "worker") {
    jobs(store, history) { configureHandlers() }
    config {
        shutdownGracePeriod = 1.seconds
        this.clock = clock
        dispatcher = StandardTestDispatcher(testScheduler)
        defaults {
            pollInterval = poll
            this.prepareWindow = prepareWindow
            leaseDuration = lease
            heartbeatInterval = heartbeat
            this.retry = retry
        }
    }
}

internal class SchedulerClock(private val scheduler: TestCoroutineScheduler) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(scheduler.currentTime)
}
