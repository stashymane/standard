package dev.stashy.worker

import dev.stashy.worker.handler.JobsRegistry
import dev.stashy.worker.internal.Queue
import dev.stashy.worker.store.JobHistory
import dev.stashy.worker.store.JobQueue

/**
 * Configuration DSL for [Worker].
 *
 * Each [jobs] block binds a [JobQueue] to the handlers it may execute, optionally recording their
 * execution history. Several blocks may use the same queue, each with independent concurrency and
 * history.
 */
public class WorkerBuilder internal constructor() {
    internal val bindings = mutableListOf<QueueBinding<*>>()
    internal val config = WorkerConfig()

    private val handlerIds = mutableSetOf<String>()

    /**
     * Registers jobs that run against [queue] and optionally records their attempts in [history].
     *
     * A worker may use several queues, for example a shared queue for cluster-wide work and an
     * in-memory queue for node-local jobs. A handler may only be registered once across all blocks.
     * [queue] may be passed to several [jobs] blocks; each block has its own [history] and tracks
     * concurrency independently.
     */
    public fun jobs(
        queue: JobQueue<*>,
        history: JobHistory? = null,
        configure: JobsRegistry<*>.() -> Unit,
    ): Unit {
        bindings += binding(queue, history, configure)
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
        bindings = bindings,
    )

    private fun <P : Any> binding(
        queue: JobQueue<P>,
        history: JobHistory?,
        configure: JobsRegistry<*>.() -> Unit,
    ): QueueBinding<P> {
        val registry = JobsRegistry<P>(handlerIds)
        registry.configure()
        return QueueBinding(queue, history, registry)
    }
}

internal fun <P : Any> QueueBinding<P>.toQueue(worker: Worker, config: WorkerConfig): Queue<P> =
    Queue(worker, queue, history, handlers, config)

internal class QueueBinding<P : Any>(
    val queue: JobQueue<P>,
    val history: JobHistory?,
    val handlers: JobsRegistry<P>,
)
