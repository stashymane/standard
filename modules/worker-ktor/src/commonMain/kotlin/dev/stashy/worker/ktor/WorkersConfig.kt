package dev.stashy.worker.ktor

import dev.stashy.worker.HandlerRegistry
import dev.stashy.worker.Worker
import dev.stashy.worker.WorkerConfig
import dev.stashy.worker.job.JobBuilder
import dev.stashy.worker.job.JobStore
import kotlin.uuid.Uuid

/**
 * Configuration for the Ktor [Workers] plugin.
 *
 * A [store] is required. Use [dev.stashy.worker.job.InMemoryJobStore] for a single-node
 * application or provide a database-backed implementation for multi-server deployments.
 */
public class WorkersConfig {
    public var id: String = Uuid.generateV7().toString()
    public var store: JobStore? = null

    private val handlerBlocks = mutableListOf<HandlerRegistry.() -> Unit>()
    private val configBlocks = mutableListOf<WorkerConfig.() -> Unit>()

    @PublishedApi
    internal val jobs: MutableList<suspend (Worker) -> Unit> = mutableListOf()

    /**
     * Registers handlers by the serial name of their config type.
     */
    public fun handlers(configure: HandlerRegistry.() -> Unit): Unit {
        handlerBlocks += configure
    }

    /**
     * Configures the worker's runtime settings.
     */
    public fun config(configure: WorkerConfig.() -> Unit): Unit {
        configBlocks += configure
    }

    /**
     * Defines a job through the [JobBuilder] DSL and synchronizes it on startup.
     */
    public inline fun <reified C : Any> job(
        id: String,
        jobConfig: C,
        noinline configure: JobBuilder.() -> Unit,
    ): Unit {
        jobs += { worker -> worker.schedule(id, jobConfig, configure) }
    }

    internal fun createWorker(): Worker {
        val store = requireNotNull(store) {
            "The Workers plugin requires a JobStore; set store in the install block"
        }
        return Worker(id = id, store = store) {
            handlers {
                handlerBlocks.forEach { block -> block(this) }
            }
            config {
                configBlocks.forEach { block -> block(this) }
            }
        }
    }
}
