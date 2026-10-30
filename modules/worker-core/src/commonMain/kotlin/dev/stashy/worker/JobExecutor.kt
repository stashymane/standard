package dev.stashy.worker

import dev.stashy.worker.job.JobHistory
import dev.stashy.worker.job.JobQueue
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Executes one claimed job and owns its lease, preparation, and occurrence decision.
 */
internal class JobExecutor(
    private val workerId: String,
    private val store: JobQueue,
    private val history: JobHistory?,
    private val handlers: JobsRegistry,
    private val config: WorkerConfig,
) {
    suspend fun execute(
        claim: JobQueue.Claimed,
        started: CompletableDeferred<Unit>,
    ): Unit {
        val definition = claim.job.definition
        var startedAt = config.clock.now()
        val binding = handlers.find(definition.handlerId)
        if (binding == null) {
            val message = "No job registered for '${definition.handlerId}'"
            config.logger.log(WorkerLogger.Level.ERROR, "Job '${definition.id.value}': $message")
            val now = config.clock.now()
            applyDecision(claim, startedAt, now, OccurrenceDecision.terminal(claim, now, message))
            return
        }

        val context = Handler.Context(
            jobId = definition.id,
            workerId = workerId,
            attempt = claim.attempt,
            scheduledAt = claim.scheduledAt,
            clock = config.clock,
        )

        coroutineScope {
            val executionScope = this
            val heartbeat = launch {
                heartbeat(claim) {
                    executionScope.cancel(CancellationException("Lease lost for job '${definition.id.value}'"))
                }
            }
            var prepared: Handler.Prepared? = null
            try {
                val execution = try {
                    binding.prepare(store, claim, context)
                } catch (invalid: InvalidHandlerPayloadException) {
                    val message = "Could not decode job config: ${invalid.message ?: "invalid config"}"
                    config.logger.log(
                        WorkerLogger.Level.ERROR,
                        "Job '${definition.id.value}': $message",
                        invalid,
                    )
                    val now = config.clock.now()
                    applyDecision(claim, startedAt, now, OccurrenceDecision.terminal(claim, now, message))
                    return@coroutineScope
                }
                prepared = execution

                awaitScheduled(claim)

                startedAt = config.clock.now()
                if (definition.startsTooLate(startedAt, claim.scheduledAt)) {
                    val message = "Job started ${startedAt - claim.scheduledAt} after its scheduled instant"
                    config.logger.log(WorkerLogger.Level.WARN, "Job '${definition.id.value}': $message")
                    applyDecision(claim, startedAt, startedAt, OccurrenceDecision.Fail(message))
                    return@coroutineScope
                }

                started.complete(Unit)
                execution.execute(context)

                val finishedAt = config.clock.now()
                applyDecision(claim, startedAt, finishedAt, OccurrenceDecision.success(claim, finishedAt))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val now = config.clock.now()
                val message = error.message ?: error::class.simpleName ?: "Unknown handler failure"
                config.logger.log(WorkerLogger.Level.ERROR, "Job '${definition.id.value}' failed", error)
                val decision = OccurrenceDecision.failure(claim, now, message)
                val applied = applyDecision(claim, startedAt, now, decision)
                if (applied && decision is OccurrenceDecision.Retry) {
                    config.logger.log(
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
    suspend fun expire(claim: JobQueue.Claimed): Unit {
        val now = config.clock.now()
        val definition = claim.job.definition
        val message = "Lease expired after ${claim.attempt} attempt(s)"
        val decision = OccurrenceDecision.failure(claim, now, message)
        if (decision !is OccurrenceDecision.Retry) {
            config.logger.log(WorkerLogger.Level.WARN, "Job '${definition.id.value}': ${decision.error}")
        }
        val applied = applyDecision(claim, now, now, decision)
        if (applied && decision is OccurrenceDecision.Retry) {
            config.logger.log(
                WorkerLogger.Level.WARN,
                "Job '${definition.id.value}' will retry at ${decision.retryAt} ($message)",
            )
        }
    }

    /**
     * Releases a claimed job during worker shutdown, making it runnable again immediately.
     */
    suspend fun release(claim: JobQueue.Claimed, reason: String): Unit {
        val now = config.clock.now()
        applyDecision(claim, now, now, OccurrenceDecision.Retry(now, reason))
    }

    private suspend fun applyDecision(
        claim: JobQueue.Claimed,
        startedAt: Instant,
        finishedAt: Instant,
        decision: OccurrenceDecision,
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
        } ?: false

        if (!applied) {
            config.logger.log(
                WorkerLogger.Level.WARN,
                "Lost the lease for job '$id' before its decision could be stored",
            )
            return false
        }

        recordHistory(claim, startedAt, finishedAt, decision)
        return true
    }

    private suspend fun recordHistory(
        claim: JobQueue.Claimed,
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
            status = if (decision is OccurrenceDecision.Complete && decision.error == null) {
                JobHistory.Status.SUCCEEDED
            } else {
                JobHistory.Status.FAILED
            },
            message = decision.error,
        )
        attemptStore("Could not record history for job '${entry.jobId.value}'") { history.record(entry) }
    }

    private suspend fun dispose(claim: JobQueue.Claimed, prepared: Handler.Prepared) {
        val caller = currentCoroutineContext()
        try {
            withContext(NonCancellable) {
                prepared.dispose()
            }
        } catch (cancelled: CancellationException) {
            caller.ensureActive()
            config.logger.log(
                WorkerLogger.Level.WARN,
                "Cleanup was cancelled for prepared job '${claim.job.definition.id.value}'",
                cancelled,
            )
        } catch (error: Exception) {
            config.logger.log(
                WorkerLogger.Level.ERROR,
                "Could not dispose prepared job '${claim.job.definition.id.value}'",
                error,
            )
        }
        caller.ensureActive()
    }

    private suspend fun awaitScheduled(claim: JobQueue.Claimed): Unit {
        val executeAt = maxOf(claim.scheduledAt, claim.job.nextRun ?: claim.scheduledAt)
        while (true) {
            val remaining = executeAt - config.clock.now()
            if (remaining <= Duration.ZERO) return
            delay(remaining)
        }
    }

    private suspend fun heartbeat(claim: JobQueue.Claimed, onLeaseLost: () -> Unit): Unit {
        val id = claim.job.definition.id
        var leaseUntil = claim.job.lock?.expiresAt ?: (config.clock.now() + config.leaseDuration)

        while (currentCoroutineContext().isActive) {
            delay(config.heartbeatInterval)
            val now = config.clock.now()

            val renewed = try {
                store.renew(claim, now, config.leaseDuration)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                config.logger.log(WorkerLogger.Level.WARN, "Could not renew lease for job '${id.value}'", error)
                null
            }

            when (renewed) {
                true -> leaseUntil = now + config.leaseDuration
                false -> {
                    config.logger.log(
                        WorkerLogger.Level.WARN,
                        "Lost the lease for job '${id.value}'; cancelling execution"
                    )
                    onLeaseLost()
                    return
                }

                null -> if (now >= leaseUntil) {
                    config.logger.log(
                        WorkerLogger.Level.WARN,
                        "Lease for job '${id.value}' could not be renewed; cancelling execution",
                    )
                    onLeaseLost()
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
            config.logger.log(WorkerLogger.Level.ERROR, failureMessage, error)
            null
        }
}
