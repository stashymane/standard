package dev.stashy.worker

import dev.stashy.worker.job.JobStore
import dev.stashy.worker.job.LateExecutionPolicy
import dev.stashy.worker.schedule.Schedule
import dev.stashy.worker.schedule.nextOccurrence
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Executes one claimed job and owns its lease, preparation, retry, and failure transitions.
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
            finishOccurrence(claim, config.clock.now(), message)
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
                    finishOccurrence(claim, config.clock.now(), message)
                    return@coroutineScope
                }
                prepared = execution

                awaitScheduled(claim)
                if (isTooLate(claim)) return@coroutineScope

                started.complete(Unit)
                execution.execute(context)

                val finishedAt = config.clock.now()
                val nextRunAt = definition.schedule.nextOccurrence(claim.scheduledAt, finishedAt)
                val updated = attemptStore("Could not complete job '${definition.id.value}'") {
                    store.complete(claim, finishedAt, nextRunAt)
                } ?: false

                if (!updated) {
                    config.logger.log(
                        WorkerLogger.Level.WARN,
                        "Lost the lease for job '${definition.id.value}' before completion",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                handleFailure(claim, error)
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

        if (claim.attempt >= definition.retry.maxAttempts) {
            config.logger.log(
                WorkerLogger.Level.WARN,
                "Job '${definition.id.value}' exhausted its attempts ($message)"
            )
            finishOccurrence(claim, now, message)
            return
        }

        val retryAt = now + definition.retry.delayFor(claim.attempt)
        val updated = attemptStore("Could not reschedule expired job '${definition.id.value}'") {
            store.reschedule(claim, now, retryAt, message)
        } ?: false

        if (updated) {
            config.logger.log(
                WorkerLogger.Level.WARN,
                "Job '${definition.id.value}' will retry at $retryAt ($message)"
            )
        }
    }

    /**
     * Releases a claimed job during worker shutdown.
     */
    suspend fun release(claim: JobStore.Claimed, reason: String): Unit {
        val now = config.clock.now()
        val retryAt = now + claim.job.definition.retry.delayFor(claim.attempt)
        attemptStore("Could not release job '${claim.job.definition.id.value}'") {
            store.reschedule(claim, now, retryAt, reason)
        }
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

    private suspend fun handleFailure(claim: JobStore.Claimed, error: Throwable): Unit {
        val now = config.clock.now()
        val definition = claim.job.definition
        val message = error.message ?: error::class.simpleName ?: "Unknown handler failure"
        config.logger.log(WorkerLogger.Level.ERROR, "Job '${definition.id.value}' failed", error)

        if (claim.attempt >= definition.retry.maxAttempts) {
            finishOccurrence(claim, now, "Attempts exhausted: $message")
            return
        }

        val retryAt = now + definition.retry.delayFor(claim.attempt)
        val updated = attemptStore("Could not schedule retry for job '${definition.id.value}'") {
            store.reschedule(claim, now, retryAt, message)
        } ?: false

        if (updated) {
            config.logger.log(WorkerLogger.Level.WARN, "Job '${definition.id.value}' will retry at $retryAt")
        }
    }

    /**
     * Ends an occurrence: one-shot jobs fail, while recurring jobs advance to their next occurrence.
     */
    private suspend fun finishOccurrence(claim: JobStore.Claimed, now: Instant, error: String) {
        val definition = claim.job.definition
        val next = definition.schedule.nextOccurrence(claim.scheduledAt, now)
        val updated = attemptStore("Could not finish job '${definition.id.value}'") {
            if (next == null) {
                store.fail(claim, now, error)
            } else {
                store.complete(claim, now, next, error)
            }
        } ?: false

        if (!updated) {
            config.logger.log(
                WorkerLogger.Level.WARN,
                "Lost the lease for job '${definition.id.value}' while finishing it",
            )
        }
    }

    private suspend fun isTooLate(claim: JobStore.Claimed): Boolean {
        val definition = claim.job.definition
        if (definition.schedule !is Schedule.Once) return false

        val policy = definition.latePolicy as? LateExecutionPolicy.FailAfter ?: return false
        val lateness = config.clock.now() - claim.scheduledAt
        return when {
            lateness <= policy.tolerance -> false
            else -> {
                val message = "Job started $lateness after its scheduled instant"
                config.logger.log(WorkerLogger.Level.WARN, "Job '${definition.id.value}': $message")
                finishOccurrence(claim, config.clock.now(), message)
                true
            }
        }
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
