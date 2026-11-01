package dev.stashy.worker.internal

import dev.stashy.worker.JobsConfig
import dev.stashy.worker.Worker
import dev.stashy.worker.WorkerConfig
import dev.stashy.worker.WorkerLogger
import dev.stashy.worker.handler.Handler
import dev.stashy.worker.handler.JobsRegistry
import dev.stashy.worker.log
import dev.stashy.worker.store.JobHistory
import dev.stashy.worker.store.JobQueue
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Executes one claimed job and owns its lease, preparation, and occurrence decision.
 */
internal class JobExecutor<P : Any>(
    private val worker: Worker,
    private val store: JobQueue<P>,
    private val history: JobHistory?,
    private val handlers: JobsRegistry<P>,
    private val workerConfig: WorkerConfig,
    private val config: JobsConfig,
) {
    suspend fun execute(
        claim: JobQueue.Claimed<P>,
        started: CompletableDeferred<Unit>,
    ): Unit {
        val definition = claim.job.definition
        var startedAt = workerConfig.clock.now()
        val binding = handlers.find(definition.handlerId)

        if (claim.job.cancelRequested) {
            applyDecision(claim, startedAt, startedAt, OccurrenceDecision.Cancelled())
            return
        }

        if (binding == null) {
            val message = "No job registered for '${definition.handlerId}'"
            workerConfig.logger.log(WorkerLogger.Level.ERROR, "Job '${definition.id.value}': $message")
            val now = workerConfig.clock.now()
            applyDecision(claim, startedAt, now, OccurrenceDecision.terminal(claim, now, message))
            return
        }

        val context = contextFor(claim)

        coroutineScope {
            val executionScope = this
            val heartbeat = launch {
                heartbeat(claim) { cause -> executionScope.cancel(cause) }
            }
            var prepared: Handler.Prepared? = null
            try {
                val execution = try {
                    binding.prepare(store, claim, context)
                } catch (invalid: InvalidHandlerPayloadException) {
                    val message = "Could not decode job config: ${invalid.message ?: "invalid config"}"
                    workerConfig.logger.log(
                        WorkerLogger.Level.ERROR,
                        "Job '${definition.id.value}': $message",
                        invalid,
                    )
                    val now = workerConfig.clock.now()
                    applyDecision(claim, startedAt, now, OccurrenceDecision.terminal(claim, now, message))
                    return@coroutineScope
                }
                prepared = execution

                awaitScheduled(claim)

                startedAt = workerConfig.clock.now()
                if (definition.startsTooLate(startedAt, claim.scheduledAt)) {
                    val message = "Job started ${startedAt - claim.scheduledAt} after its scheduled instant"
                    workerConfig.logger.log(WorkerLogger.Level.WARN, "Job '${definition.id.value}': $message")
                    applyDecision(claim, startedAt, startedAt, OccurrenceDecision.Fail(message), prepared)
                    return@coroutineScope
                }

                started.complete(Unit)
                execution.execute(context)
                currentCoroutineContext().ensureActive()

                val finishedAt = workerConfig.clock.now()
                applyDecision(claim, startedAt, finishedAt, OccurrenceDecision.success(claim, finishedAt), prepared)
            } catch (cancelled: CancellationException) {
                if (cancelled is JobCancelledException) {
                    val now = workerConfig.clock.now()
                    withContext(NonCancellable) {
                        applyDecision(claim, startedAt, now, OccurrenceDecision.Cancelled(), prepared)
                    }
                }
                throw cancelled
            } catch (error: Throwable) {
                val now = workerConfig.clock.now()
                val message = error.message ?: error::class.simpleName ?: "Unknown handler failure"
                workerConfig.logger.log(WorkerLogger.Level.ERROR, "Job '${definition.id.value}' failed", error)
                val decision = OccurrenceDecision.failure(claim, now, message)
                val applied = applyDecision(claim, startedAt, now, decision, prepared)
                if (applied && decision is OccurrenceDecision.Retry) {
                    workerConfig.logger.log(
                        WorkerLogger.Level.WARN,
                        "Job '${definition.id.value}' will retry at ${decision.retryAt}",
                    )
                }
            } finally {
                heartbeat.cancel()
                prepared?.let { dispose(claim, it) }
            }
        }
    }

    /**
     * Handles a lease that expired before the worker could finish the job.
     */
    suspend fun expire(claim: JobQueue.Claimed<P>): Unit {
        val now = workerConfig.clock.now()
        val definition = claim.job.definition
        if (claim.job.cancelRequested) {
            applyDecision(claim, now, now, OccurrenceDecision.Cancelled())
            return
        }

        val message = "Lease expired after ${claim.attempt} attempt(s)"
        val decision = OccurrenceDecision.failure(claim, now, message)
        if (decision !is OccurrenceDecision.Retry) {
            workerConfig.logger.log(WorkerLogger.Level.WARN, "Job '${definition.id.value}': ${decision.error}")
        }
        val applied = applyDecision(claim, now, now, decision)
        if (applied && decision is OccurrenceDecision.Retry) {
            workerConfig.logger.log(
                WorkerLogger.Level.WARN,
                "Job '${definition.id.value}' will retry at ${decision.retryAt} ($message)",
            )
        }
    }

    /**
     * Releases a claimed job during worker shutdown, making it runnable again immediately.
     */
    suspend fun release(claim: JobQueue.Claimed<P>, reason: String): Unit {
        val now = workerConfig.clock.now()
        applyDecision(claim, now, now, OccurrenceDecision.Retry(now, reason))
    }

    private suspend fun applyDecision(
        claim: JobQueue.Claimed<P>,
        startedAt: Instant,
        finishedAt: Instant,
        decision: OccurrenceDecision,
        prepared: Handler.Prepared? = null,
    ): Boolean {
        val id = claim.job.definition.id.value
        val applied = when (decision) {
            is OccurrenceDecision.Complete -> attemptStore("Could not complete job '$id'") {
                store.complete(claim, decision.nextRunAt)
            }

            is OccurrenceDecision.Retry -> attemptStore("Could not reschedule job '$id'") {
                store.reschedule(claim, decision.retryAt)
            }

            is OccurrenceDecision.Fail -> attemptStore("Could not fail job '$id'") {
                store.fail(claim)
            }

            is OccurrenceDecision.Cancelled -> attemptStore("Could not cancel job '$id'") {
                store.fail(claim)
            }
        } ?: false

        if (!applied) {
            workerConfig.logger.log(
                WorkerLogger.Level.WARN,
                "Lost the lease for job '$id' before its decision could be stored",
            )
            return false
        }

        recordHistory(claim, startedAt, finishedAt, decision)
        notifyFinalized(claim, decision, prepared)
        return true
    }

    private fun contextFor(claim: JobQueue.Claimed<P>): Handler.Context = Handler.Context(
        jobId = claim.job.definition.id,
        worker = worker,
        attempt = claim.attempt,
        scheduledAt = claim.scheduledAt,
        clock = workerConfig.clock,
    )

    private suspend fun notifyFinalized(
        claim: JobQueue.Claimed<P>,
        decision: OccurrenceDecision,
        prepared: Handler.Prepared?,
    ) {
        val outcome = decision.terminalOutcome() ?: return
        if (prepared == null) return
        try {
            prepared.finalize(contextFor(claim), outcome)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            workerConfig.logger.log(
                WorkerLogger.Level.ERROR,
                "Could not finalize job '${claim.job.definition.id.value}'",
                error,
            )
        }
    }

    private suspend fun recordHistory(
        claim: JobQueue.Claimed<P>,
        startedAt: Instant,
        finishedAt: Instant,
        decision: OccurrenceDecision,
    ): Unit {
        val history = history ?: return
        val entry = JobHistory.Entry(
            jobId = claim.job.definition.id,
            handlerId = claim.job.definition.handlerId,
            attempt = claim.attempt,
            scheduledAt = claim.scheduledAt,
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = when {
                decision is OccurrenceDecision.Cancelled -> JobHistory.Status.CANCELLED
                decision is OccurrenceDecision.Complete && decision.error == null -> JobHistory.Status.SUCCEEDED
                else -> JobHistory.Status.FAILED
            },
            message = decision.error,
        )
        attemptStore("Could not record history for job '${entry.jobId.value}'") { history.record(entry) }
    }

    private suspend fun dispose(claim: JobQueue.Claimed<P>, prepared: Handler.Prepared) {
        val caller = currentCoroutineContext()
        try {
            withContext(NonCancellable) {
                prepared.dispose()
            }
        } catch (cancelled: CancellationException) {
            caller.ensureActive()
            workerConfig.logger.log(
                WorkerLogger.Level.WARN,
                "Cleanup was cancelled for prepared job '${claim.job.definition.id.value}'",
                cancelled,
            )
        } catch (error: Exception) {
            workerConfig.logger.log(
                WorkerLogger.Level.ERROR,
                "Could not dispose prepared job '${claim.job.definition.id.value}'",
                error,
            )
        }
        caller.ensureActive()
    }

    private suspend fun awaitScheduled(claim: JobQueue.Claimed<P>): Unit {
        val executeAt = maxOf(claim.scheduledAt, claim.job.nextRun ?: claim.scheduledAt)
        while (true) {
            val remaining = executeAt - workerConfig.clock.now()
            if (remaining <= Duration.ZERO) return
            delay(remaining)
        }
    }

    private suspend fun heartbeat(
        claim: JobQueue.Claimed<P>,
        onTerminate: (CancellationException) -> Unit,
    ): Unit {
        val id = claim.job.definition.id
        var leaseUntil = claim.job.lock?.expiresAt ?: (workerConfig.clock.now() + config.leaseDuration)

        while (currentCoroutineContext().isActive) {
            delay(config.heartbeatInterval)
            val now = workerConfig.clock.now()

            val renewal = try {
                store.renew(claim, now, config.leaseDuration)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                workerConfig.logger.log(WorkerLogger.Level.WARN, "Could not renew lease for job '${id.value}'", error)
                null
            }

            when (renewal) {
                JobQueue.Renewal.RENEWED -> leaseUntil = now + config.leaseDuration

                JobQueue.Renewal.LOST -> {
                    workerConfig.logger.log(
                        WorkerLogger.Level.WARN,
                        "Lost the lease for job '${id.value}'; cancelling execution"
                    )
                    onTerminate(CancellationException("Lease lost for job '${id.value}'"))
                    return
                }

                JobQueue.Renewal.CANCELLATION_REQUESTED -> {
                    workerConfig.logger.log(
                        WorkerLogger.Level.INFO,
                        "Cancellation requested for job '${id.value}'; cancelling execution",
                    )
                    onTerminate(JobCancelledException())
                    return
                }

                null -> if (now >= leaseUntil) {
                    workerConfig.logger.log(
                        WorkerLogger.Level.WARN,
                        "Lease for job '${id.value}' could not be renewed; cancelling execution",
                    )
                    onTerminate(CancellationException("Lease for job '${id.value}' could not be renewed"))
                    return
                }
            }
        }
    }

    private suspend fun <T> attemptStore(failureMessage: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            workerConfig.logger.log(WorkerLogger.Level.ERROR, failureMessage, error)
            null
        }
}
