package dev.stashy.worker.internal

import dev.stashy.worker.JobsConfig
import dev.stashy.worker.Worker
import dev.stashy.worker.WorkerConfig
import dev.stashy.worker.WorkerLogger
import dev.stashy.worker.handler.JobsRegistry
import dev.stashy.worker.job.Job
import dev.stashy.worker.job.QueuedJob
import dev.stashy.worker.store.JobHistory
import dev.stashy.worker.store.JobQueue
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.*
import kotlinx.coroutines.Job as CoroutineJob

/**
 * A [JobQueue], its optional [JobHistory], and the handlers it may execute.
 */
internal class Queue<P : Any>(
    private val worker: Worker,
    val store: JobQueue<P>,
    private val history: JobHistory?,
    val handlers: JobsRegistry<P>,
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
    fun cancelLocal(id: Job.Id): Unit {
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

    fun clearExecutions(): Unit {
        inFlight.value = emptyMap()
    }

    private fun purgeCompleted(): Unit {
        inFlight.update { current -> current.filterValues { !it.coroutine.isCompleted } }
    }

    private suspend fun sweepExpired(): Unit {
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

    private suspend fun claimRunnable(executionScope: CoroutineScope): Unit {
        val handlerIds = handlers.ids()
        if (handlerIds.isEmpty()) return

        val available = config.maxConcurrentJobs - inFlight.value.size
        if (available <= 0) return

        val claimed = try {
            store.claim(
                JobQueue.ClaimRequest(
                    now = workerConfig.clock.now(),
                    supportedHandlerIds = handlerIds,
                    prepareWindow = config.prepareWindow,
                    lease = config.leaseDuration,
                    workerId = worker.id,
                ),
                limit = available,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            workerConfig.logger.log(WorkerLogger.Level.ERROR, "Could not claim worker jobs", error)
            return
        }

        for (claim in claimed) {
            val started = CompletableDeferred<Unit>()
            val execution = executionScope.launch(start = CoroutineStart.LAZY) {
                executor.execute(claim, started)
            }
            inFlight.update { current ->
                current + (claim.job.definition.id to Execution(execution, started) { reason ->
                    executor.release(claim, reason)
                })
            }
            execution.start()
        }
    }

    class Execution(
        val coroutine: CoroutineJob,
        val started: CompletableDeferred<Unit>,
        val release: suspend (reason: String) -> Unit,
    )
}
