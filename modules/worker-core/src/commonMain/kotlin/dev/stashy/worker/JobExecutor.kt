package dev.stashy.worker

import dev.stashy.worker.job.JobStore
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Executes one claimed job and owns its lease, preparation, and occurrence decision.
 */
internal class JobExecutor(
    private val workerId: String,
    private val store: JobStore,
    private val handlers: JobsRegistry,
    private val config: WorkerConfig,
) {
    suspend fun execute(
        claim: JobStore.Claimed,
        started: CompletableDeferred<Unit>,
    ): Unit {
        val definition = claim.job.definition
        val binding = handlers.find(definition.handlerId)
        if (binding == null) {
            val message = "No job registered for '${definition.handlerId}'"
            config.logger.log(WorkerLogger.Level.ERROR, "Job '${definition.id.value}': $message")
            val now = config.clock.now()
            applyDecision(claim, now, OccurrenceDecision.terminal(claim, now, message))
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
                    applyDecision(claim, now, OccurrenceDecision.terminal(claim, now, message))
                    return@coroutineScope
                }
                prepared = execution

                awaitScheduled(claim)

                val startedAt = config.clock.now()
                if (definition.startsTooLate(startedAt, claim.scheduledAt)) {
                    val message = "Job started ${startedAt - claim.scheduledAt} after its scheduled instant"
                    config.logger.log(WorkerLogger.Level.WARN, "Job '${definition.id.value}': $message")
                    applyDecision(claim, startedAt, OccurrenceDecision.Fail(message))
                    return@coroutineScope
                }

                started.complete(Unit)
                execution.execute(context)

                val finishedAt = config.clock.now()
                applyDecision(claim, finishedAt, OccurrenceDecision.success(claim, finishedAt))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val now = config.clock.now()
                val message = error.message ?: error::class.simpleName ?: "Unknown handler failure"
                config.logger.log(WorkerLogger.Level.ERROR, "Job '${definition.id.value}' failed", error)
                val decision = OccurrenceDecision.failure(claim, now, message)
                val applied = applyDecision(claim, now, decision)
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
    suspend fun expire(claim: JobStore.Claimed): Unit {
        val now = config.clock.now()
        val definition = claim.job.definition
        val message = "Lease expired after ${claim.attempt} attempt(s)"
        val decision = OccurrenceDecision.failure(claim, now, message)
        if (decision !is OccurrenceDecision.Retry) {
            config.logger.log(WorkerLogger.Level.WARN, "Job '${definition.id.value}': ${decision.error}")
        }
        val applied = applyDecision(claim, now, decision)
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
    suspend fun release(claim: JobStore.Claimed, reason: String): Unit {
        val now = config.clock.now()
        applyDecision(claim, now, OccurrenceDecision.Retry(now, reason))
    }

    private suspend fun applyDecision(claim: JobStore.Claimed, now: Instant, decision: OccurrenceDecision): Boolean {
        val id = claim.job.definition.id.value
        val applied = when (decision) {
            is OccurrenceDecision.Complete -> attemptStore("Could not complete job '$id'") {
                store.complete(claim, now, decision.nextRunAt, decision.error)
            }

            is OccurrenceDecision.Retry -> attemptStore("Could not reschedule job '$id'") {
                store.reschedule(claim, now, decision.retryAt, decision.error)
            }

            is OccurrenceDecision.Fail -> attemptStore("Could not fail job '$id'") {
                store.fail(claim, now, decision.error)
            }
        } ?: false

        if (!applied) {
            config.logger.log(
                WorkerLogger.Level.WARN,
                "Lost the lease for job '$id' before its decision could be stored",
            )
        }
        return applied
    }

    private suspend fun dispose(claim: JobStore.Claimed, prepared: Handler.Prepared) {
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

    private suspend fun awaitScheduled(claim: JobStore.Claimed): Unit {
        val executeAt = maxOf(claim.scheduledAt, claim.job.nextRun ?: claim.scheduledAt)
        while (true) {
            val remaining = executeAt - config.clock.now()
            if (remaining <= Duration.ZERO) return
            delay(remaining)
        }
    }

    private suspend fun heartbeat(claim: JobStore.Claimed, onLeaseLost: () -> Unit): Unit {
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
