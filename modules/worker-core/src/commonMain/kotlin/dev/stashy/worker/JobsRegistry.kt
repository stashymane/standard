package dev.stashy.worker

/**
 * Registry of the jobs a worker is able to execute.
 *
 * Jobs are keyed by the serial name of their config type. Use `@SerialName` to keep that name
 * stable when a config class is moved or renamed.
 */
@JobsDsl
public class JobsRegistry internal constructor() {
    private val bindings = mutableMapOf<String, HandlerBinding>()

    /**
     * Registers a handler whose block runs at the scheduled instant.
     */
    public inline fun <reified C : Any> job(
        noinline block: suspend HandlerScope.(Handler.Context, C) -> Unit,
    ): Unit {
        register(dev.stashy.worker.job(block))
    }

    /**
     * Registers a handler with a preparation stage.
     */
    public inline fun <reified C : Any> prepared(
        noinline block: suspend PreparationScope.(Handler.Context, C) -> Handler.Execution,
    ): Unit {
        register(dev.stashy.worker.prepared(block))
    }

    public fun <C : Any> register(handler: Handler<C>): Unit {
        val binding = handlerBinding(handler)
        require(binding.id !in bindings) { "Job '${binding.id}' is already registered" }
        bindings[binding.id] = binding
    }

    internal fun ids(): Set<String> = bindings.keys.toSet()

    internal fun find(id: String): HandlerBinding? = bindings[id]

    internal fun require(id: String): HandlerBinding = bindings[id]
        ?: error("No job registered for '$id'")
}
