package dev.stashy.data.source

import dev.stashy.data.DataSource

internal class TransformingDataSource<T, R>(
    private val source: DataSource<T>,
    private val transform: (T) -> R,
) : DataSource<R> {
    override fun getOrNull(): R? = source.getOrNull()?.let(transform)

    override suspend fun await(): Result<R> = source.await().mapCatching(transform)
}

fun <T, R> DataSource<T>.map(transform: (T) -> R): DataSource<R> =
    TransformingDataSource(this, transform)
