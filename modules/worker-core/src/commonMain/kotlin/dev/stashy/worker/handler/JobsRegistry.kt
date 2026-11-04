package dev.stashy.worker.handler

import dev.stashy.worker.JobsDsl
import dev.stashy.worker.internal.HandlerBinding
import dev.stashy.worker.internal.handlerBinding
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

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
    private typealias JobBuilder<C> = suspend JobScope.(Handler.Context, C) -> Handler.Builder
    
    private val bindings = mutableMapOf<String, HandlerBinding<P>>()

    /**
     * Registers a handler whose block runs when the job is claimed.
     */
    public fun <C : Any> job(
        serializer: KSerializer<C>,
        block: JobBuilder<C>
    ): Unit = register(object : Handler<C> {
        override val configSerializer: KSerializer<C> = serializer

        override suspend fun prepare(context: Handler.Context, config: C): Handler.Prepared {
            val scope = JobScope()
            return try {
                scope.block(context, config).build(cleanup = {
                    withContext(NonCancellable) { scope.dispose() }
                })
            } catch (error: Throwable) {
                withContext(NonCancellable) { scope.dispose() }
                throw error
            }
        }
    })

    /**
     * Registers a handler whose block runs when the job is claimed.
     */
    public inline fun <reified C : Any> job(noinline block: JobBuilder<C>): Unit =
        job(serializer<C>(), block)

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
