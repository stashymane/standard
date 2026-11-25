package dev.stashy.worker.internal

import dev.stashy.worker.*
import dev.stashy.worker.handler.JobsRegistry
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.QueuedJob
import dev.stashy.worker.pool.ConcurrencyPool
import dev.stashy.worker.pool.PoolRequest
import dev.stashy.worker.pool.describe
import dev.stashy.worker.store.JobHistory
import dev.stashy.worker.store.JobQueue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.atomicfu.update
import kotlinx.coroutines.*
import kotlinx.coroutines.Job as CoroutineJob

/**
 * A [JobQueue], its optional [JobHistory], the handlers it may execute, and the [ConcurrencyPool]
 * that budget their execution.
 */
internal class Queue<P : Any>(
    private val worker: Worker,
    val store: JobQueue<P>,
    private val history: JobHistory?,
    val handlers: JobsRegistry<P>,
    private val pool: ConcurrencyPool,
    private val workerConfig: WorkerConfig,
    val config: JobsConfig,
) {
    init {
        config.validate()
    }

    private val executor = JobExecutor(worker, store, history, handlers, workerConfig, config)
    private val inFlight = atomic<Map<Job.Id, Execution>>(emptyMap())

    /**
     * A snapshot of the executions this queue currently owns.
     */
    val executions: List<Execution>
        get() = inFlight.value.values.toList()

    suspend fun poll(executionScope: CoroutineScope) {
        purgeCompleted()
        sweepExpired()
        claimRunnable(executionScope)
    }

    /**
     * Cancels the local execution of [id], if this queue owns it.
     */
    fun cancelLocal(id: Job.Id) {
        inFlight.value[id]?.coroutine?.cancel(JobCancelledException())
    }

    /**
     * Records the cancellation of a pending job, which no execution will report.
     */
    suspend fun recordCancellation(job: QueuedJob<*>) {
        val history = history ?: return
        val now = workerConfig.clock.now()
        val entry = JobHistory.Entry(
            jobId = job.definition.id,
            handlerId = job.definition.handlerId,
            attempt = job.attempt,
            scheduledAt = job.scheduledAt ?: job.nextRun ?: now,
            startedAt = now,
            finishedAt = now,
            status = JobHistory.Status.CANCELLED,
            message = "Cancelled",
        )
        try {
            history.record(entry)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            workerConfig.logger.log(
                WorkerLogger.Level.ERROR,
                "Could not record cancellation for job '${entry.jobId.value}'",
                error,
            )
        }
    }

    fun clearExecutions() {
        inFlight.getAndUpdate { emptyMap() }.values.forEach { it.releaseSlot() }
    }

    private fun purgeCompleted() {
        for ((id, execution) in inFlight.value) {
            if (!execution.coroutine.isCompleted) continue
            inFlight.update { current -> current - id }
            execution.releaseSlot()
        }
    }

    private suspend fun sweepExpired() {
        val expired = try {
            store.expired(workerConfig.clock.now())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            workerConfig.logger.log(WorkerLogger.Level.ERROR, "Could not sweep expired worker leases", error)
            return
        }

        for (claim in expired) {
            if (inFlight.value.containsKey(claim.job.definition.id)) continue
            executor.expire(claim)
        }
    }

    private suspend fun claimRunnable(executionScope: CoroutineScope) {
        val handlerIds = handlers.ids()
        if (handlerIds.isEmpty()) return

        val granted = try {
            withTimeoutOrNull(config.pollInterval) {
                pool.admit(PoolRequest(pool.capacity, handlerIds, store))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            workerConfig.logger.log(
                WorkerLogger.Level.ERROR,
                "Could not admit jobs through the ${pool.describe()}",
                error,
            )
            return
        } ?: return
        val admitted = granted.coerceIn(0, pool.capacity)
        if (admitted != granted) {
            workerConfig.logger.log(
                WorkerLogger.Level.ERROR,
                "The ${pool.describe()} granted $granted permits for a request of ${pool.capacity}",
            )
        }
        if (admitted == 0) return

        val claimed = try {
            store.claim(
                JobQueue.ClaimRequest(
                    now = workerConfig.clock.now(),
                    supportedHandlerIds = handlerIds,
                    prepareWindow = config.prepareWindow,
                    lease = config.leaseDuration,
                    workerId = worker.id,
                ),
                limit = admitted,
            )
        } catch (cancelled: CancellationException) {
            pool.release(admitted)
            throw cancelled
        } catch (error: Exception) {
            workerConfig.logger.log(WorkerLogger.Level.ERROR, "Could not claim worker jobs", error)
            pool.release(admitted)
            return
        }

        if (claimed.size < admitted) pool.release(admitted - claimed.size)

        for (claim in claimed) {
            val started = CompletableDeferred<Unit>()
            val execution = Execution(
                coroutine = executionScope.launch(start = CoroutineStart.LAZY) {
                    executor.execute(claim, started)
                },
                started = started,
                pool = pool,
                release = { reason -> executor.release(claim, reason) },
            )
            execution.coroutine.invokeOnCompletion { execution.releaseSlot() }
            inFlight.update { current -> current + (claim.job.definition.id to execution) }
            execution.coroutine.start()
        }
    }

    class Execution(
        val coroutine: CoroutineJob,
        val started: CompletableDeferred<Unit>,
        val release: suspend (reason: String) -> Unit,
        private val pool: ConcurrencyPool,
    ) {
        private val holdsSlot = atomic(true)

        fun releaseSlot() {
            if (holdsSlot.compareAndSet(expect = true, update = false)) pool.release(1)
        }
    }
}
