package dev.stashy.data

import dev.stashy.data.source.SuspendingDataSource

/**
 * Lazy data source.
 */
interface DataSource<out T> {
    /** Returns a value if one is available without suspending, otherwise null. */
    fun getOrNull(): T?

    /**
     * Fetches the data source if necessary, suspending until a [Result] is available.
     */
    suspend fun await(): Result<T>
}

fun <T> dataSource(load: suspend () -> T): DataSource<T> = SuspendingDataSource(load)

inline fun <T, R> DataSource<T>.getOrElse(transform: (T) -> R, orElse: () -> R) =
    getOrNull()?.let(transform) ?: orElse()
