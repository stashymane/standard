package dev.stashy.worker.handler

import dev.stashy.worker.JobsDsl

/**
 * Scope available to handlers.
 *
 * Resources are disposed in reverse acquisition order.
 */
@JobsDsl
public open class HandlerScope @PublishedApi internal constructor() {
    private val cleanups = mutableListOf<suspend () -> Unit>()

    /**
     * Acquires [acquire] immediately and registers its cleanup.
     *
     * The returned [Resource] can be used with property delegation and [Resource.onDispose].
     */
    public suspend fun <T> resource(acquire: suspend () -> T): Resource<T> {
        val resource = Resource(acquire())
        cleanups += { resource.dispose() }
        return resource
    }

    /**
     * Registers a cleanup action that runs after the handler, or when it is cancelled.
     */
    public fun defer(cleanup: suspend () -> Unit) {
        cleanups += cleanup
    }

    @PublishedApi
    internal suspend fun dispose() {
        var failure: Throwable? = null
        for (cleanup in cleanups.asReversed()) {
            try {
                cleanup()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}
