package dev.stashy.worker

import dev.stashy.worker.job.JobStore

/**
 * Configuration DSL for [Worker].
 *
 * Each [jobs] block binds a [JobStore] to the handlers it may execute.
 */
public class WorkerBuilder internal constructor() {
    internal val queues = mutableListOf<Pair<JobStore, JobsRegistry>>()
    internal val config = WorkerConfig()

    private val handlerIds = mutableSetOf<String>()

    /**
     * Registers jobs that run against [store].
     *
     * A worker may use several stores, for example a shared store for cluster-wide work and an
     * in-memory store for node-local jobs. A handler may only be registered once across all
     * stores, and [store] may only be passed to one [jobs] block.
     */
    public fun jobs(store: JobStore, configure: JobsRegistry.() -> Unit): Unit {
        require(queues.none { it.first === store }) {
            "Jobs for this JobStore are already registered"
        }
        val registry = JobsRegistry(handlerIds)
        registry.configure()
        queues += store to registry
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
        queues = queues.map { (store, handlers) -> Queue(id, store, handlers, config) },
    )
}
