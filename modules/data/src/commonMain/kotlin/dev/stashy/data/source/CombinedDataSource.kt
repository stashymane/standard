package dev.stashy.data.source

import dev.stashy.data.DataSource
import dev.stashy.data.DataSourceState
import dev.stashy.data.rethrowCancellation
import dev.stashy.data.toDataSourceState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@PublishedApi
internal class CombinedDataSource<R : Any>(
    private val sources: List<DataSource<Any>>,
    private val transform: (List<Any>) -> R,
) : DataSource<R> {
    override fun getOrNull(): R? {
        val values = ArrayList<Any>(sources.size)
        for (source in sources) values += source.getOrNull() ?: return null
        return transform(values)
    }

    override suspend fun await(): Result<R> = coroutineScope {
        runCatching {
            val values = sources.map { async { it.await().getOrThrow() } }.awaitAll()
            transform(values)
        }.rethrowCancellation()
    }

    override val state: Flow<DataSourceState<R>> = combine(
        sources.map(DataSource<Any>::state)
    ) { states ->
        combineStates(states.toList(), transform)
    }
}

private fun <R : Any> combineStates(
    states: List<DataSourceState<Any>>,
    transform: (List<Any>) -> R,
): DataSourceState<R> {
    states.forEach { if (it is DataSourceState.Failure) return DataSourceState.Failure(it.cause) }
    if (states.any { it is DataSourceState.Loading }) return DataSourceState.Loading

    val values = states.map { (it as DataSourceState.Success<Any>).value }
    return runCatching { transform(values) }.toDataSourceState()
}

public inline fun <A : Any, B : Any, R : Any> combine(
    first: DataSource<A>,
    second: DataSource<B>,
    crossinline transform: (A, B) -> R,
): DataSource<R> = CombinedDataSource(listOf(first, second)) { values ->
    @Suppress("UNCHECKED_CAST")
    transform(values[0] as A, values[1] as B)
}

public inline fun <A : Any, B : Any, C : Any, R : Any> combine(
    first: DataSource<A>,
    second: DataSource<B>,
    third: DataSource<C>,
    crossinline transform: (A, B, C) -> R,
): DataSource<R> = CombinedDataSource(listOf(first, second, third)) { values ->
    @Suppress("UNCHECKED_CAST")
    transform(values[0] as A, values[1] as B, values[2] as C)
}

public inline fun <A : Any, B : Any, C : Any, D : Any, R : Any> combine(
    first: DataSource<A>,
    second: DataSource<B>,
    third: DataSource<C>,
    fourth: DataSource<D>,
    crossinline transform: (A, B, C, D) -> R,
): DataSource<R> = CombinedDataSource(listOf(first, second, third, fourth)) { values ->
    @Suppress("UNCHECKED_CAST")
    transform(values[0] as A, values[1] as B, values[2] as C, values[3] as D)
}
