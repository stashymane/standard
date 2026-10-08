package dev.stashy.data.source

import dev.stashy.data.CachedDataSource
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred

internal class CachingDataSource<T>(
    private val load: suspend () -> Result<T>,
) : CachedDataSource<T> {
    private val cached = atomic<T?>(null)
    private val inFlight = atomic<CompletableDeferred<Result<T>>?>(null)

    override fun getOrNull(): T? = cached.value

    override suspend fun await(): Result<T> {
        while (true) {
            cached.value?.let { return Result.success(it) }

            val existing = inFlight.value
            if (existing != null) {
                return existing.await()
            }

            val created = CompletableDeferred<Result<T>>()
            if (!inFlight.compareAndSet(null, created)) {
                continue
            }

            return try {
                val result = load()
                if (result.isSuccess) {
                    cached.value = result.getOrThrow()
                }
                inFlight.compareAndSet(created, null)
                created.complete(result)
                result
            } catch (e: Throwable) {
                inFlight.compareAndSet(created, null)
                created.completeExceptionally(e)
                throw e
            }
        }
    }

    override suspend fun preload() {
        await()
    }
}
