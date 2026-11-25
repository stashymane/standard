package dev.stashy.worker.handler

import dev.stashy.worker.JobsDsl
import dev.stashy.worker.internal.HandlerBinding
import dev.stashy.worker.internal.handlerBinding
import dev.stashy.worker.pool.ConcurrencyPool
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
    private val pools = mutableMapOf<String, ConcurrencyPool>()

    /**
     * Registers a handler whose block runs when the job is claimed.
     *
     * [pool] overrides the pool its `jobs` block uses.
     */
    public fun <C : Any> job(
        pool: ConcurrencyPool? = null,
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
    }, pool)

    /**
     * Registers a handler whose block runs when the job is claimed.
     *
     * [pool] overrides the pool its `jobs` block uses.
     */
    public inline fun <reified C : Any> job(
        pool: ConcurrencyPool? = null,
        noinline block: JobBuilder<C>,
    ): Unit = job(pool, serializer<C>(), block)

    public fun <C : Any> register(handler: Handler<C>, pool: ConcurrencyPool? = null) {
        val binding = handlerBinding<P, C>(handler)
        require(binding.id !in registeredIds) { "Job '${binding.id}' is already registered" }
        registeredIds += binding.id
        bindings[binding.id] = binding
        if (pool != null) pools[binding.id] = pool
    }

    internal fun ids(): Set<String> = bindings.keys.toSet()

    internal fun find(id: String): HandlerBinding<P>? = bindings[id]

    internal fun require(id: String): HandlerBinding<P> = bindings[id]
        ?: error("No job registered for '$id'")

    /**
     * Splits this registry into one registry per pool, keeping registration order.
     */
    internal fun groups(default: ConcurrencyPool): List<Pair<ConcurrencyPool, JobsRegistry<P>>> {
        val groups = mutableListOf<Pair<ConcurrencyPool, MutableSet<String>>>()
        for (id in bindings.keys) {
            val pool = pools[id] ?: default
            val existing = groups.firstOrNull { (candidate, _) -> candidate === pool }
            if (existing == null) groups += pool to mutableSetOf(id) else existing.second += id
        }
        return groups.map { (pool, ids) -> pool to subset(ids) }
    }

    private fun subset(ids: Set<String>): JobsRegistry<P> {
        val registry = JobsRegistry<P>(registeredIds)
        for (id in ids) {
            bindings[id]?.let { registry.bindings[id] = it }
        }
        return registry
    }
}
