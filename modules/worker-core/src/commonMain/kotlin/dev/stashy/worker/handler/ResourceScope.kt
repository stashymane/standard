package dev.stashy.worker.handler

import dev.stashy.worker.JobsDsl
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * A scope that owns resources and their cleanup actions.
 *
 * Resources are disposed in reverse acquisition order.
 */
@JobsDsl
public interface ResourceScope {
    /**
     * Acquires a [Resource] via [acquire] immediately and registers its cleanup.
     */
    public suspend fun <T> resource(acquire: suspend () -> T): Resource<T>

    /**
     * Registers a cleanup action that runs after the scope is disposed.
     */
    public fun defer(cleanup: suspend () -> Unit)

    /**
     * Disposes of all registered resources in reverse order.
     */
    public suspend fun dispose()

    public companion object {
        /**
         * Creates a new [ResourceScope].
         */
        public operator fun invoke(): ResourceScope = DefaultResourceScope()

        /**
         * Runs [block] with a new [ResourceScope] and disposes it when the block completes.
         *
         * The scope is disposed even if [block] fails. A failure during disposal is rethrown if the block
         * succeeded, or suppressed by the block's failure otherwise.
         */
        public suspend operator fun <R> invoke(block: suspend ResourceScope.() -> R): R {
            val scope = ResourceScope()
            val result = runCatching { block(scope) }

            try {
                scope.dispose()
            } catch (disposal: Throwable) {
                val failure = result.exceptionOrNull() ?: throw disposal
                failure.addSuppressed(disposal)
            }

            return result.getOrThrow()
        }
    }
}


private class DefaultResourceScope : ResourceScope {
    private val cleanups = mutableListOf<suspend () -> Unit>()

    override suspend fun <T> resource(acquire: suspend () -> T): Resource<T> {
        val resource = Resource(acquire())
        cleanups += resource::dispose
        return resource
    }

    override fun defer(cleanup: suspend () -> Unit) {
        cleanups += cleanup
    }

    override suspend fun dispose(): Unit = withContext(NonCancellable) {
        var failure: Throwable? = null
        for (cleanup in cleanups.asReversed()) {
            try {
                cleanup()
            } catch (error: Throwable) {
                if (failure == null)
                    failure = error
                else
                    failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}
