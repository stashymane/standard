package dev.stashy.worker

import dev.stashy.worker.job.JobHistory
import dev.stashy.worker.job.JobQueue

/**
 * Configuration DSL for [Worker].
 *
 * Each [jobs] block binds a [JobQueue] to the handlers it may execute, optionally recording their
 * execution history.
 */
public class WorkerBuilder internal constructor() {
    internal val bindings = mutableListOf<QueueBinding>()
    internal val config = WorkerConfig()

    private val handlerIds = mutableSetOf<String>()

    /**
     * Registers jobs that run against [queue] and optionally records their attempts in [history].
     *
     * A worker may use several queues, for example a shared queue for cluster-wide work and an
     * in-memory queue for node-local jobs. A handler may only be registered once across all
     * queues, and [queue] may only be passed to one [jobs] block. [history] may be shared between
     * blocks.
     */
    public fun jobs(
        queue: JobQueue,
        history: JobHistory? = null,
        configure: JobsRegistry.() -> Unit,
    ): Unit {
        require(bindings.none { it.queue === queue }) {
            "Jobs for this JobQueue are already registered"
        }
        val registry = JobsRegistry(handlerIds)
        registry.configure()
        bindings += QueueBinding(queue, history, registry)
    }

    /**
     * Configures the worker's runtime settings.
     */
    public fun config(configure: WorkerConfig.() -> Unit): Unit {
        config.configure()
    }

    internal fun build(id: String): Worker = Worker(
        id = id,
        config = config,
        queues = bindings.map { Queue(id, it.queue, it.history, it.handlers, config) },
    )
}

internal class QueueBinding(
    val queue: JobQueue,
    val history: JobHistory?,
    val handlers: JobsRegistry,
)
