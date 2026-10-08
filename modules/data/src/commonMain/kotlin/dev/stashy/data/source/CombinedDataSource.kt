package dev.stashy.data.source

import dev.stashy.data.DataSource
import dev.stashy.data.rethrowCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class CombinedDataSource<A, B, R>(
    private val first: DataSource<A>,
    private val second: DataSource<B>,
    private val transform: (A, B) -> R,
) : DataSource<R> {
    override fun getOrNull(): R? {
        val a = first.getOrNull() ?: return null
        val b = second.getOrNull() ?: return null
        return transform(a, b)
    }

    override suspend fun await(): Result<R> = coroutineScope {
        val a = async { first.await().getOrThrow() }
        val b = async { second.await().getOrThrow() }
        runCatching { transform(a.await(), b.await()) }.rethrowCancellation()
    }
}

fun <A, B, R> combine(
    first: DataSource<A>,
    second: DataSource<B>,
    transform: (A, B) -> R,
): DataSource<R> = CombinedDataSource(first, second, transform)
