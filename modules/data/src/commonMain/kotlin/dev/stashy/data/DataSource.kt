package dev.stashy.data

import dev.stashy.data.source.SuspendingDataSource

/**
 * Lazy data source.
 */
public interface DataSource<out T> {
    /** Returns a value if one is available without suspending, otherwise null. */
    public fun getOrNull(): T?

    /**
     * Fetches the data source if necessary, suspending until a [Result] is available.
     */
    public suspend fun await(): Result<T>
}

public fun <T> dataSource(load: suspend () -> T): DataSource<T> = SuspendingDataSource(load)

public inline fun <T, R> DataSource<T>.getOrElse(transform: (T) -> R, orElse: () -> R): R =
    getOrNull()?.let(transform) ?: orElse()
