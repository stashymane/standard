package dev.stashy.worker

import dev.stashy.worker.job.Job
import dev.stashy.worker.job.JobBuilder
import dev.stashy.worker.job.JobStore
import dev.stashy.worker.job.defineJob
import dev.stashy.worker.schedule.Schedule
import dev.stashy.worker.schedule.firstOccurrence
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job as CoroutineJob

/**
 * Polls one or more [JobStore]s, claims due jobs, and delegates execution to a [JobExecutor] per
 * store.
 *
 * Each [WorkerBuilder.jobs] block binds a store to the handlers that store may execute. Jobs are
 * selected by the serial name of their config type. [start] may be used by any coroutine host; the
 * Ktor integration uses the application scope so that shutdown cancels the worker and its graceful
 * cleanup is awaited.
 */
public class Worker internal constructor(
    public val id: String,
    private val config: WorkerConfig,
    private val queues: List<Queue>,
) {
    init {
        require(id.isNotBlank()) { "Worker id must not be blank" }
        config.validate()
    }

    private val queueByHandler: Map<String, Queue> = buildMap {
        for (queue in queues) {
            for (handlerId in queue.handlers.ids()) {
                check(put(handlerId, queue) == null) {
                    "Job '$handlerId' is registered in multiple queues"
                }
            }
        }
    }

    public companion object {
        /**
         * Creates a [Worker] using the [WorkerBuilder] DSL.
         */
        public operator fun invoke(
            id: String = Uuid.generateV7().toString(),
            configure: WorkerBuilder.() -> Unit = {},
        ): Worker = WorkerBuilder().apply(configure).build(id)
    }

    private val runJob = atomic<CoroutineJob?>(null)

    public fun start(scope: CoroutineScope): Unit {
        val executionScope = CoroutineScope(
            scope.coroutineContext.minusKey(CoroutineJob) + SupervisorJob() + config.dispatcher,
        )

        val job = scope.launch(config.dispatcher, start = CoroutineStart.LAZY) {
            try {
                runLoop(executionScope)
            } finally {
                withContext(NonCancellable) {
                    shutdown(executionScope)
                }
            }
        }
        if (!runJob.compareAndSet(null, job)) {
            job.cancel()
            error("Worker is already started")
        }
        job.start()
    }

    public fun requestStop(): Unit {
        runJob.value?.cancel()
    }

    public suspend fun stop(): Unit {
        val job = runJob.value ?: return
        job.cancelAndJoin()
        runJob.compareAndSet(job, null)
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
        val queue = queueFor(handlerId)
        val startAt = at ?: config.clock.now()
        val definition = Job.Definition(
            id = Job.Id.random(),
            handlerId = handlerId,
            schedule = Schedule.Once(startAt),
            retry = config.retry,
        )
        if (!queue.store.create(definition, jobConfig, serializer, startAt)) {
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
        val queue = queueFor(definition.handlerId)

        val now = config.clock.now()
        val existing = queue.store.get(definition.id)
        if (existing == null) {
            if (!queue.store.create(definition, jobConfig, serializer, definition.schedule.firstOccurrence(now))) {
                config.logger.log(
                    WorkerLogger.Level.WARN,
                    "Job '${definition.id.value}' already exists while syncing",
                )
            }
            return definition.id
        }

        val scheduleChanged = existing.definition.schedule != definition.schedule
        val nextRunAt = if (scheduleChanged) definition.schedule.firstOccurrence(now) else null
        if (!queue.store.updateDefinition(definition, jobConfig, serializer, nextRunAt)) {
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
        val definition = defineJob(handlerId, id, config.retry, configure)
        return sync(definition, jobConfig, serializer)
    }

    private fun queueFor(handlerId: String): Queue =
        queueByHandler[handlerId] ?: error("No job registered for '$handlerId'")

    private suspend fun runLoop(executionScope: CoroutineScope) {
        while (currentCoroutineContext().isActive) {
            for (queue in queues) {
                try {
                    queue.poll(executionScope)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    config.logger.log(WorkerLogger.Level.ERROR, "Worker queue failed", error)
                }
            }
            delay(config.pollInterval)
        }
    }

    private suspend fun shutdown(executionScope: CoroutineScope) {
        val running = queues.flatMap { queue ->
            queue.inFlight.values.map { queue to it }
        }
        if (running.isNotEmpty()) {
            val toRelease = linkedSetOf<Pair<Queue, Queue.Execution>>()

            val notStarted = running.filterNot { it.second.started.isCompleted }
            notStarted.forEach {
                it.second.coroutine.cancel()
                toRelease += it
            }

            val executing = running.filter { it.second.started.isCompleted }
            if (executing.isNotEmpty()) {
                config.logger.log(
                    WorkerLogger.Level.INFO,
                    "Waiting up to ${config.shutdownGracePeriod} for ${executing.size} executing worker job(s)",
                )
                withTimeoutOrNull(config.shutdownGracePeriod) {
                    executing.map { it.second.coroutine }.joinAll()
                }
            }

            val remaining = running.filterNot { it.second.coroutine.isCompleted }
            remaining.forEach {
                it.second.coroutine.cancel()
                toRelease += it
            }
            withTimeoutOrNull(1.seconds) {
                toRelease.map { it.second.coroutine }.joinAll()
            }

            for ((queue, execution) in toRelease) {
                queue.release(execution, "Worker stopped")
            }
        }

        withTimeoutOrNull(1.seconds) {
            executionScope.coroutineContext[CoroutineJob]?.cancelAndJoin()
        }
        queues.forEach { it.inFlight.clear() }
    }
}
