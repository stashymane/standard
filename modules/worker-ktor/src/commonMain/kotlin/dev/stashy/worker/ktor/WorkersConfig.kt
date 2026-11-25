package dev.stashy.worker.ktor

import dev.stashy.worker.JobsConfig
import dev.stashy.worker.Worker
import dev.stashy.worker.WorkerConfig
import dev.stashy.worker.WorkerLogger
import dev.stashy.worker.handler.JobsRegistry
import dev.stashy.worker.pool.ConcurrencyPool
import dev.stashy.worker.store.JobHistory
import dev.stashy.worker.store.JobQueue
import kotlin.uuid.Uuid

/**
 * Configuration for the Ktor [Workers] plugin.
 */
public class WorkersConfig {
    public var id: String = Uuid.generateV7().toString()

    private val jobBlocks = mutableListOf<JobBlock>()
    private val configBlocks = mutableListOf<WorkerConfig.() -> Unit>()

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
        jobBlocks += JobBlock(queue, history, pool, config, configure)
    }

    /**
     * Configures the worker's runtime settings.
     */
    public fun config(configure: WorkerConfig.() -> Unit): Unit {
        configBlocks += configure
    }

    internal fun createWorker(
        defaultLogger: () -> WorkerLogger
    ): Worker = Worker(id = id) {
        jobBlocks.forEach { block ->
            jobs(block.queue, block.history, block.pool, block.config) { block.configure(this) }
        }
        config {
            configBlocks.forEach { block -> block(this) }

            if (logger === WorkerLogger.None) logger = defaultLogger()
        }
    }

    private class JobBlock(
        val queue: JobQueue<*>,
        val history: JobHistory?,
        val pool: ConcurrencyPool?,
        val config: JobsConfig.() -> Unit,
        val configure: JobsRegistry<*>.() -> Unit,
    )
}
