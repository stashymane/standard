package dev.stashy.worker

import dev.stashy.worker.job.Job
import dev.stashy.worker.job.JobBuilder
import dev.stashy.worker.job.JobStore
import dev.stashy.worker.job.defineJob
import dev.stashy.worker.schedule.Schedule
import dev.stashy.worker.schedule.firstOccurrence
import kotlinx.coroutines.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job as CoroutineJob

/**
 * Polls a [JobStore], claims due jobs, and delegates execution to [JobExecutor].
 *
 * Handlers are selected by the serial name of their config type. [start] may be used by any
 * coroutine host; the Ktor integration uses the application scope so that shutdown cancels the
 * worker and its graceful cleanup is awaited.
 */
public class Worker(
    public val id: String = Uuid.generateV7().toString(),
    public val store: JobStore,
    configure: Builder.() -> Unit = {},
) {
    public class Builder internal constructor() {
        internal val handlers = HandlerRegistry()
        internal val config = WorkerConfig()

        public fun handlers(configure: HandlerRegistry.() -> Unit): Unit {
            handlers.configure()
        }

        public fun config(configure: WorkerConfig.() -> Unit): Unit {
            config.configure()
        }
    }

    init {
        require(id.isNotBlank()) { "Worker id must not be blank" }
    }

    private val builder = Builder().apply(configure)
    private val handlers: HandlerRegistry = builder.handlers
    private val config: WorkerConfig = builder.config.also { it.validate() }
    private val executor = JobExecutor(id, store, handlers, config)
    private val inFlight = mutableMapOf<Job.Id, Execution>()

    @Volatile
    private var runJob: CoroutineJob? = null

    public fun start(scope: CoroutineScope): Unit {
        check(runJob == null) { "Worker is already started" }

        val executionScope = CoroutineScope(
            scope.coroutineContext.minusKey(CoroutineJob) + SupervisorJob() + config.dispatcher,
        )

        runJob = scope.launch(config.dispatcher) {
            try {
                runLoop(executionScope)
            } finally {
                withContext(NonCancellable) {
                    shutdown(executionScope)
                }
            }
        }
    }

    public fun requestStop(): Unit {
        runJob?.cancel()
    }

    public suspend fun stop(): Unit {
        val job = runJob ?: return
        job.cancelAndJoin()
        runJob = null
    }

    /**
     * Creates [definition] when missing, or updates it when its definition changed.
     *
     * Runtime state is preserved. A changed schedule resets the next occurrence and attempt count,
     * but a completed one-shot job stays completed when its definition is unchanged.
     */
    public suspend inline fun <reified C : Any> sync(
        definition: Job.Definition,
        jobConfig: C,
    ): Job.Id = sync(definition, jobConfig, serializer())

    /**
     * Schedules a one-shot execution of [jobConfig].
     */
    public suspend inline fun <reified C : Any> enqueue(
        jobConfig: C,
        at: Instant? = null,
    ): Job.Id = enqueue(jobConfig, serializer(), at)

    /**
     * Defines and synchronizes a job using the [JobBuilder] DSL.
     */
    public suspend inline fun <reified C : Any> schedule(
        id: String,
        jobConfig: C,
        noinline configure: JobBuilder.() -> Unit,
    ): Job.Id = schedule(Job.Id(id), jobConfig, serializer(), configure)

    @PublishedApi
    internal suspend fun <C : Any> enqueue(
        jobConfig: C,
        serializer: KSerializer<C>,
        at: Instant?,
    ): Job.Id {
        val handlerId = serializer.descriptor.serialName
        handlers.require(handlerId)
        val startAt = at ?: config.clock.now()
        val definition = Job.Definition(
            id = Job.Id.random(),
            handlerId = handlerId,
            schedule = Schedule.Once(startAt),
            retry = config.retry,
        )
        if (!store.create(definition, jobConfig, serializer, startAt)) {
            error("Could not enqueue job '${definition.id.value}'")
        }
        return definition.id
    }

    @PublishedApi
    internal suspend fun <C : Any> sync(
        definition: Job.Definition,
        jobConfig: C,
        serializer: KSerializer<C>,
    ): Job.Id {
        require(serializer.descriptor.serialName == definition.handlerId) {
            "Job '${definition.id.value}' targets handler '${definition.handlerId}' " +
                    "but its config serializer is '${serializer.descriptor.serialName}'"
        }
        handlers.require(definition.handlerId)

        val now = config.clock.now()
        val existing = store.get(definition.id)
        if (existing == null) {
            if (!store.create(definition, jobConfig, serializer, definition.schedule.firstOccurrence(now))) {
                config.logger.log(
                    WorkerLogger.Level.WARN,
                    "Job '${definition.id.value}' already exists while syncing",
                )
            }
            return definition.id
        }

        val scheduleChanged = existing.definition.schedule != definition.schedule
        val nextRunAt = if (scheduleChanged) definition.schedule.firstOccurrence(now) else null
        if (!store.updateDefinition(definition, jobConfig, serializer, nextRunAt)) {
            config.logger.log(WorkerLogger.Level.WARN, "Could not update synced job '${definition.id.value}'")
        }
        return definition.id
    }

    @PublishedApi
    internal suspend fun <C : Any> schedule(
        id: Job.Id,
        jobConfig: C,
        serializer: KSerializer<C>,
        configure: JobBuilder.() -> Unit,
    ): Job.Id {
        val handlerId = serializer.descriptor.serialName
        handlers.require(handlerId)
        val definition = defineJob(handlerId, id, config.retry, configure)
        return sync(definition, jobConfig, serializer)
    }

    private suspend fun runLoop(executionScope: CoroutineScope) {
        while (currentCoroutineContext().isActive) {
            try {
                purgeCompleted()
                sweepExpired()
                claimRunnable(executionScope)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                config.logger.log(WorkerLogger.Level.ERROR, "Worker loop failed", error)
            }
            delay(config.pollInterval)
        }
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

        while (inFlight.size < config.maxConcurrentJobs) {
            val claimed = try {
                store.claim(
                    JobStore.ClaimRequest(
                        now = config.clock.now(),
                        supportedHandlerIds = handlerIds,
                        prepareWindow = config.prepareWindow,
                        lease = config.leaseDuration,
                        workerId = id,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                config.logger.log(WorkerLogger.Level.ERROR, "Could not claim worker jobs", error)
                return
            } ?: return

            val execution = executionScope.launch { executor.execute(claimed) }
            inFlight[claimed.job.definition.id] = Execution(claimed, execution)
        }
    }

    private suspend fun shutdown(executionScope: CoroutineScope) {
        val running = inFlight.values.toList()
        if (running.isNotEmpty()) {
            config.logger.log(
                WorkerLogger.Level.INFO,
                "Waiting up to ${config.shutdownGracePeriod} for ${running.size} worker job(s)",
            )
            withTimeoutOrNull(config.shutdownGracePeriod) {
                running.map { it.coroutine }.joinAll()
            }

            val remaining = inFlight.values.filterNot { it.coroutine.isCompleted }
            remaining.forEach { it.coroutine.cancel() }
            withTimeoutOrNull(1.seconds) {
                remaining.map { it.coroutine }.joinAll()
            }

            for (execution in remaining) {
                executor.release(execution.claim, "Worker stopped")
            }
        }

        withTimeoutOrNull(1.seconds) {
            executionScope.coroutineContext[CoroutineJob]?.cancelAndJoin()
        }
        inFlight.clear()
    }

    private class Execution(
        val claim: JobStore.Claimed,
        val coroutine: CoroutineJob,
    )
}
