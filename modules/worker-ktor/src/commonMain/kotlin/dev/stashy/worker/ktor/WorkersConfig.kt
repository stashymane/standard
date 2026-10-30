package dev.stashy.worker.ktor

import dev.stashy.worker.JobsRegistry
import dev.stashy.worker.Worker
import dev.stashy.worker.WorkerConfig
import dev.stashy.worker.job.JobStore
import kotlin.uuid.Uuid

/**
 * Configuration for the Ktor [Workers] plugin.
 *
 * Jobs are registered with [jobs], which binds a handler set to a [JobStore]. Use a database-backed
 * store for work shared across the cluster and an in-memory store for node-local jobs.
 *
 * Jobs are scheduled through [Worker.schedule] and [Worker.enqueue] after the plugin is installed.
 */
public class WorkersConfig {
    public var id: String = Uuid.generateV7().toString()

    private val jobBlocks = mutableListOf<Pair<JobStore, JobsRegistry.() -> Unit>>()
    private val configBlocks = mutableListOf<WorkerConfig.() -> Unit>()

    /**
     * Registers jobs that run against [store].
     */
    public fun jobs(store: JobStore, configure: JobsRegistry.() -> Unit): Unit {
        require(jobBlocks.none { it.first === store }) {
            "Jobs for this JobStore are already registered"
        }
        jobBlocks += store to configure
    }

    /**
     * Configures the worker's runtime settings.
     */
    public fun config(configure: WorkerConfig.() -> Unit): Unit {
        configBlocks += configure
    }

    internal fun createWorker(): Worker = Worker(id = id) {
        jobBlocks.forEach { (store, block) ->
            jobs(store) { block(this) }
        }
        config {
            configBlocks.forEach { block -> block(this) }
        }
    }
}
