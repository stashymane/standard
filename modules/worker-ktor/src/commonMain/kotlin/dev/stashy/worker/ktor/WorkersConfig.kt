package dev.stashy.worker.ktor

import dev.stashy.worker.JobsRegistry
import dev.stashy.worker.Worker
import dev.stashy.worker.WorkerConfig
import dev.stashy.worker.job.JobHistory
import dev.stashy.worker.job.JobQueue
import kotlin.uuid.Uuid

/**
 * Configuration for the Ktor [Workers] plugin.
 *
 * Jobs are registered with [jobs], which binds a handler set to a [JobQueue] and optionally records
 * their execution history. Use a database-backed queue for work shared across the cluster and an
 * in-memory queue for node-local jobs.
 *
 * Jobs are scheduled through [Worker.schedule] and [Worker.enqueue] after the plugin is installed.
 */
public class WorkersConfig {
    public var id: String = Uuid.generateV7().toString()

    private val jobBlocks = mutableListOf<JobBlock>()
    private val configBlocks = mutableListOf<WorkerConfig.() -> Unit>()

    /**
     * Registers jobs that run against [queue] and optionally records their attempts in [history].
     *
     * A queue may only be registered once; [history] may be shared between blocks.
     */
    public fun jobs(
        queue: JobQueue<*>,
        history: JobHistory? = null,
        configure: JobsRegistry<*>.() -> Unit,
    ): Unit {
        require(jobBlocks.none { it.queue === queue }) {
            "Jobs for this JobQueue are already registered"
        }
        jobBlocks += JobBlock(queue, history, configure)
    }

    /**
     * Configures the worker's runtime settings.
     */
    public fun config(configure: WorkerConfig.() -> Unit): Unit {
        configBlocks += configure
    }

    internal fun createWorker(): Worker = Worker(id = id) {
        jobBlocks.forEach { block ->
            jobs(block.queue, block.history) { block.configure(this) }
        }
        config {
            configBlocks.forEach { block -> block(this) }
        }
    }

    private class JobBlock(
        val queue: JobQueue<*>,
        val history: JobHistory?,
        val configure: JobsRegistry<*>.() -> Unit,
    )
}
