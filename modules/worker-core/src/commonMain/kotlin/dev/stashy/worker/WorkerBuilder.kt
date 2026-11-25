package dev.stashy.worker

import dev.stashy.worker.handler.JobsRegistry
import dev.stashy.worker.internal.Queue
import dev.stashy.worker.pool.ConcurrencyPool
import dev.stashy.worker.pool.pool
import dev.stashy.worker.store.JobHistory
import dev.stashy.worker.store.JobQueue

/**
 * Configuration DSL for [Worker].
 */
public class WorkerBuilder internal constructor() {
    internal val bindings = mutableListOf<QueueBinding<*>>()
    internal val config = WorkerConfig()

    private val handlerIds = mutableSetOf<String>()

    /**
     * Registers jobs that run against [queue] and optionally records their attempts in [history].
     */
    public fun jobs(
        queue: JobQueue<*>,
        history: JobHistory? = null,
        pool: ConcurrencyPool? = null,
        config: JobsConfig.() -> Unit = {},
        configure: JobsRegistry<*>.() -> Unit,
    ): Unit {
        bindings += bind(queue, history, pool ?: defaultPool(), config, configure)
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

    private fun <P : Any> bind(
        queue: JobQueue<P>,
        history: JobHistory?,
        pool: ConcurrencyPool,
        config: JobsConfig.() -> Unit,
        configure: JobsRegistry<*>.() -> Unit,
    ): List<QueueBinding<P>> {
        val registry = JobsRegistry<P>(handlerIds)
        registry.configure()
        return registry.groups(pool).map { (groupPool, group) ->
            QueueBinding(queue, history, group, groupPool, config)
        }
    }

    private fun defaultPool(): ConcurrencyPool = pool()
}

internal fun <P : Any> QueueBinding<P>.toQueue(worker: Worker, config: WorkerConfig): Queue<P> =
    Queue(worker, queue, history, handlers, pool, config, config.defaults.copy().apply(this.config))

internal class QueueBinding<P : Any>(
    val queue: JobQueue<P>,
    val history: JobHistory?,
    val handlers: JobsRegistry<P>,
    val pool: ConcurrencyPool,
    val config: JobsConfig.() -> Unit,
)
