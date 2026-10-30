package dev.stashy.worker.handler

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Prevents calls from a nested worker DSL scope from resolving against an outer scope.
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
public annotation class JobsDsl

/**
 * Scope available to both simple and prepared handlers.
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

/**
 * Scope available to prepared handlers.
 */
@JobsDsl
public class PreparationScope @PublishedApi internal constructor() : HandlerScope() {
    private var declared = false

    /**
     * Registers the block that runs at the scheduled instant.
     *
     * This must be the returned expression of the enclosing `prepared` block.
     */
    public fun handler(
        block: suspend Handler.Context.() -> Unit,
    ): Handler.Execution {
        check(!declared) { "handler {} may only be declared once" }
        declared = true
        return Handler.Execution { context -> context.block() }
    }
}

/**
 * A delegated resource handle with an optional cleanup action.
 */
public class Resource<T> internal constructor(
    private val value: T,
) : ReadOnlyProperty<Any?, T> {
    private var cleanup: (suspend () -> Unit)? = null

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    /**
     * Registers the cleanup action for this resource. It runs on completion, failure, or
     * cancellation, in reverse acquisition order relative to other resources.
     */
    public fun onDispose(cleanup: suspend (T) -> Unit): Resource<T> {
        check(this.cleanup == null) { "Cleanup already registered for this resource" }
        this.cleanup = { cleanup(value) }
        return this
    }

    internal suspend fun dispose() {
        cleanup?.invoke()
    }
}
