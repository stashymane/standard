package dev.stashy.worker

/**
 * Registry of the handlers a worker is able to execute.
 *
 * Handlers are keyed by the serial name of their config type. Use `@SerialName` to keep that
 * name stable when a config class is moved or renamed.
 */
public class HandlerRegistry internal constructor() {
    private val bindings = mutableMapOf<String, HandlerBinding>()

    public inline fun <reified C : Any> handler(
        noinline block: suspend (Handler.Context, C) -> Unit,
    ): Unit {
        register(dev.stashy.worker.handler(block))
    }

    public fun <C : Any> register(handler: Handler<C>): Unit {
        val binding = handlerBinding(handler)
        require(binding.id !in bindings) { "Handler '${binding.id}' is already registered" }
        bindings[binding.id] = binding
    }

    internal fun ids(): Set<String> = bindings.keys.toSet()

    internal fun find(id: String): HandlerBinding? = bindings[id]

    internal fun require(id: String): HandlerBinding = bindings[id]
        ?: error("No handler registered for '$id'")
}
