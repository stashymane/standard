package dev.stashy.data.source

import dev.stashy.data.DataSource
import dev.stashy.data.DataSourceState
import dev.stashy.data.rethrowCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class CombinedDataSource<A : Any, B : Any, R : Any>(
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

    override val state: Flow<DataSourceState<R>> =
        combine(first.state, second.state) { a, b -> combineStates(a, b, transform) }
}

private fun <A : Any, B : Any, R : Any> combineStates(
    first: DataSourceState<A>,
    second: DataSourceState<B>,
    transform: (A, B) -> R,
): DataSourceState<R> {
    if (first is DataSourceState.Failure) {
        return DataSourceState.Failure(first.cause)
    }
    if (second is DataSourceState.Failure) {
        return DataSourceState.Failure(second.cause)
    }
    if (first is DataSourceState.Loading || second is DataSourceState.Loading) {
        return DataSourceState.Loading
    }

    first as DataSourceState.Success
    second as DataSourceState.Success
    return runCatching { transform(first.value, second.value) }.fold(
        onSuccess = { DataSourceState.Success(it) },
        onFailure = { DataSourceState.Failure(it) },
    )
}

public fun <A : Any, B : Any, R : Any> combine(
    first: DataSource<A>,
    second: DataSource<B>,
    transform: (A, B) -> R,
): DataSource<R> = CombinedDataSource(first, second, transform)
