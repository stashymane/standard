package dev.stashy.worker

import dev.stashy.worker.job.Job
import dev.stashy.worker.job.JobBuilder
import dev.stashy.worker.job.JobQueue
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
 * Polls one or more [JobQueue]s, claims due jobs, and delegates execution to a [JobExecutor] per
 * queue.
 *
 * Each [WorkerBuilder.jobs] block binds a queue to the handlers that queue may execute. Jobs are
 * selected by the serial name of their config type. [start] may be used by any coroutine host; the
 * Ktor integration uses the application scope so that shutdown cancels the worker and its graceful
 * cleanup is awaited.
 */
public class Worker internal constructor(
    public val id: String,
    private val config: WorkerConfig,
    private val queues: List<Queue<*>>,
) {
    init {
        require(id.isNotBlank()) { "Worker id must not be blank" }
        config.validate()
    }

    private val queueByHandler: Map<String, Queue<*>> = buildMap {
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
     * Creates [job] when missing, or updates it when it changed.
     *
     * Runtime state is preserved. A changed schedule resets the next occurrence and attempt count.
     */
    public suspend inline fun <reified C : Any> sync(
        job: Job,
        jobConfig: C,
    ): Job.Id = sync(job, jobConfig, serializer())

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
        val job = Job(
            id = Job.Id.random(),
            handlerId = handlerId,
            schedule = Schedule.Once(startAt),
            retry = config.retry,
        )
        if (!queue.create(job, jobConfig, serializer, startAt)) {
            error("Could not enqueue job '${job.id.value}'")
        }
        return job.id
    }

    @PublishedApi
    internal suspend fun <C : Any> sync(
        job: Job,
        jobConfig: C,
        serializer: KSerializer<C>,
    ): Job.Id {
        require(serializer.descriptor.serialName == job.handlerId) {
            "Job '${job.id.value}' targets handler '${job.handlerId}' " +
                    "but its config serializer is '${serializer.descriptor.serialName}'"
        }
        val queue = queueFor(job.handlerId)

        val now = config.clock.now()
        val existing = queue.store.get(job.id)
        if (existing == null) {
            if (!queue.create(job, jobConfig, serializer, job.schedule.firstOccurrence(now))) {
                config.logger.log(
                    WorkerLogger.Level.WARN,
                    "Job '${job.id.value}' already exists while syncing",
                )
            }
            return job.id
        }

        val scheduleChanged = existing.definition.schedule != job.schedule
        val nextRunAt = if (scheduleChanged) job.schedule.firstOccurrence(now) else null
        if (!queue.update(job, jobConfig, serializer, nextRunAt)) {
            config.logger.log(WorkerLogger.Level.WARN, "Could not update synced job '${job.id.value}'")
        }
        return job.id
    }

    @PublishedApi
    internal suspend fun <C : Any> schedule(
        id: Job.Id,
        jobConfig: C,
        serializer: KSerializer<C>,
        configure: JobBuilder.() -> Unit,
    ): Job.Id {
        val handlerId = serializer.descriptor.serialName
        val job = defineJob(handlerId, id, config.retry, configure)
        return sync(job, jobConfig, serializer)
    }

    private fun queueFor(handlerId: String): Queue<*> =
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
        val running = queues.flatMap { it.inFlight.values }
        if (running.isNotEmpty()) {
            val toRelease = linkedSetOf<Queue.Execution>()

            val notStarted = running.filterNot { it.started.isCompleted }
            notStarted.forEach {
                it.coroutine.cancel()
                toRelease += it
            }

            val executing = running.filter { it.started.isCompleted }
            if (executing.isNotEmpty()) {
                config.logger.log(
                    WorkerLogger.Level.INFO,
                    "Waiting up to ${config.shutdownGracePeriod} for ${executing.size} executing worker job(s)",
                )
                withTimeoutOrNull(config.shutdownGracePeriod) {
                    executing.map { it.coroutine }.joinAll()
                }
            }

            val remaining = running.filterNot { it.coroutine.isCompleted }
            remaining.forEach {
                it.coroutine.cancel()
                toRelease += it
            }
            withTimeoutOrNull(1.seconds) {
                toRelease.map { it.coroutine }.joinAll()
            }

            for (execution in toRelease) {
                execution.release("Worker stopped")
            }
        }

        withTimeoutOrNull(1.seconds) {
            executionScope.coroutineContext[CoroutineJob]?.cancelAndJoin()
        }
        queues.forEach { it.inFlight.clear() }
    }
}

private suspend fun <C : Any, P : Any> Queue<P>.create(
    job: Job,
    config: C,
    serializer: KSerializer<C>,
    at: Instant,
): Boolean = store.create(job, store.codec.encode(config, serializer), at)

private suspend fun <C : Any, P : Any> Queue<P>.update(
    job: Job,
    config: C,
    serializer: KSerializer<C>,
    nextRunAt: Instant?,
): Boolean = store.update(job, store.codec.encode(config, serializer), nextRunAt)
