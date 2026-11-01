package dev.stashy.worker.handler

import dev.stashy.worker.JobsDsl
import dev.stashy.worker.internal.HandlerBinding
import dev.stashy.worker.internal.handlerBinding

/**
 * Registry of the jobs a worker is able to execute.
 *
 * Jobs are keyed by the serial name of their config type. Use `@SerialName` to keep that name
 * stable when a config class is moved or renamed.
 */
@JobsDsl
public class JobsRegistry<P : Any> internal constructor(
    private val registeredIds: MutableSet<String> = mutableSetOf(),
) {
    private val bindings = mutableMapOf<String, HandlerBinding<P>>()

    /**
     * Registers a handler whose block runs when the job is claimed.
     */
    public inline fun <reified C : Any> job(
        noinline block: suspend JobScope.(Handler.Context, C) -> Handler.Builder,
    ): Unit {
        register(dev.stashy.worker.handler.job(block))
    }

    public fun <C : Any> register(handler: Handler<C>): Unit {
        val binding = handlerBinding<P, C>(handler)
        require(binding.id !in registeredIds) { "Job '${binding.id}' is already registered" }
        registeredIds += binding.id
        bindings[binding.id] = binding
    }

    internal fun ids(): Set<String> = bindings.keys.toSet()

    internal fun find(id: String): HandlerBinding<P>? = bindings[id]

    internal fun require(id: String): HandlerBinding<P> = bindings[id]
        ?: error("No job registered for '$id'")
}
