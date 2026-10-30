package dev.stashy.worker.internal

import dev.stashy.worker.WorkerConfig
import dev.stashy.worker.WorkerLogger
import dev.stashy.worker.handler.JobsRegistry
import dev.stashy.worker.job.Job
import dev.stashy.worker.store.JobHistory
import dev.stashy.worker.store.JobQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job as CoroutineJob

/**
 * A [JobQueue], its optional [JobHistory], and the handlers it may execute.
 */
internal class Queue<P : Any>(
    private val workerId: String,
    val store: JobQueue<P>,
    history: JobHistory?,
    val handlers: JobsRegistry<P>,
    private val config: WorkerConfig,
) {
    private val executor = JobExecutor(workerId, store, history, handlers, config)
    val inFlight = mutableMapOf<Job.Id, Execution>()

    suspend fun poll(executionScope: CoroutineScope) {
        purgeCompleted()
        sweepExpired()
        claimRunnable(executionScope)
    }

    private fun purgeCompleted(): Unit {
        inFlight.entries.removeAll { it.value.coroutine.isCompleted }
    }

    private suspend fun sweepExpired(): Unit {
        val expired = try {
            store.expired(config.clock.now())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            config.logger.log(WorkerLogger.Level.ERROR, "Could not sweep expired worker leases", error)
            return
        }

        for (claim in expired) {
            if (inFlight.containsKey(claim.job.definition.id)) continue
            executor.expire(claim)
        }
    }

    private suspend fun claimRunnable(executionScope: CoroutineScope): Unit {
        val handlerIds = handlers.ids()
        if (handlerIds.isEmpty()) return

        val available = config.maxConcurrentJobs - inFlight.size
        if (available <= 0) return

        val claimed = try {
            store.claim(
                JobQueue.ClaimRequest(
                    now = config.clock.now(),
                    supportedHandlerIds = handlerIds,
                    prepareWindow = config.prepareWindow,
                    lease = config.leaseDuration,
                    workerId = workerId,
                ),
                limit = available,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            config.logger.log(WorkerLogger.Level.ERROR, "Could not claim worker jobs", error)
            return
        }

        for (claim in claimed) {
            val started = CompletableDeferred<Unit>()
            val execution = executionScope.launch(start = CoroutineStart.LAZY) {
                executor.execute(claim, started)
            }
            inFlight[claim.job.definition.id] = Execution(execution, started) { reason ->
                executor.release(claim, reason)
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
