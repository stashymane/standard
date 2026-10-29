package dev.stashy.worker.ktor

import dev.stashy.worker.JobsRegistry
import dev.stashy.worker.Worker
import dev.stashy.worker.WorkerConfig
import dev.stashy.worker.job.JobStore
import kotlin.uuid.Uuid

/**
 * Configuration for the Ktor [Workers] plugin.
 *
 * A [store] is required. Use [dev.stashy.worker.job.InMemoryJobStore] for a single-node
 * application or provide a database-backed implementation for multi-server deployments.
 *
 * Jobs are scheduled through [Worker.schedule] and [Worker.enqueue] after the plugin is installed.
 */
public class WorkersConfig {
    public var id: String = Uuid.generateV7().toString()
    public var store: JobStore? = null

    private val jobBlocks = mutableListOf<JobsRegistry.() -> Unit>()
    private val configBlocks = mutableListOf<WorkerConfig.() -> Unit>()

    /**
     * Registers jobs by the serial name of their config type.
     */
    public fun jobs(configure: JobsRegistry.() -> Unit): Unit {
        jobBlocks += configure
    }

    /**
     * Configures the worker's runtime settings.
     */
    public fun config(configure: WorkerConfig.() -> Unit): Unit {
        configBlocks += configure
    }

    internal fun createWorker(): Worker {
        val store = requireNotNull(store) {
            "The Workers plugin requires a JobStore; set store in the install block"
        }
        return Worker(id = id, store = store) {
            jobs {
                jobBlocks.forEach { block -> block(this) }
            }
            config {
                configBlocks.forEach { block -> block(this) }
            }
        }
    }
}
