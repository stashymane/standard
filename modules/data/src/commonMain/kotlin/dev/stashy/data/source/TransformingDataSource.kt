package dev.stashy.data.source

import dev.stashy.data.DataSource
import dev.stashy.data.DataSourceState
import dev.stashy.data.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class TransformingDataSource<T : Any, R : Any>(
    private val source: DataSource<T>,
    private val transform: (T) -> R,
) : DataSource<R> {
    override fun getOrNull(): R? = source.getOrNull()?.let(transform)

    override suspend fun await(): Result<R> = source.await().mapCatching(transform)

    override val state: Flow<DataSourceState<R>> =
        source.state.map { state -> state.map(transform) }
}

public fun <T : Any, R : Any> DataSource<T>.map(transform: (T) -> R): DataSource<R> =
    TransformingDataSource(this, transform)
