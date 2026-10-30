package dev.stashy.worker

import dev.stashy.worker.job.Job
import dev.stashy.worker.job.JobStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job as CoroutineJob

/**
 * A [JobStore] and the handlers it may execute.
 */
internal class Queue(
    private val workerId: String,
    val store: JobStore,
    val handlers: JobsRegistry,
    private val config: WorkerConfig,
) {
    private val executor = JobExecutor(workerId, store, handlers, config)
    val inFlight = mutableMapOf<Job.Id, Execution>()

    suspend fun poll(executionScope: CoroutineScope) {
        purgeCompleted()
        sweepExpired()
        claimRunnable(executionScope)
    }

    suspend fun release(execution: Execution, reason: String) {
        executor.release(execution.claim, reason)
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
                JobStore.ClaimRequest(
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
            inFlight[claim.job.definition.id] = Execution(claim, execution, started)
            execution.start()
        }
    }

    class Execution(
        val claim: JobStore.Claimed,
        val coroutine: CoroutineJob,
        val started: CompletableDeferred<Unit>,
    )
}
