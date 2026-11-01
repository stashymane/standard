package dev.stashy.worker.handler

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

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
