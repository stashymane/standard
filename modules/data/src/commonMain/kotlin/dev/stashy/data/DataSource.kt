package dev.stashy.data

import dev.stashy.data.source.SuspendingDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Lazy data source.
 */
public interface DataSource<out T : Any> {
    /** Returns a value if one is available without suspending, otherwise null. */
    public fun getOrNull(): T?

    /**
     * Fetches the data source if necessary, suspending until a [Result] is available.
     */
    public suspend fun await(): Result<T>

    /**
     * Observes the state of this source.
     *
     * The default implementation is cold and performs one load for every collection. A
     * [SharedDataSource] exposes a hot [kotlinx.coroutines.flow.StateFlow] with the same state.
     */
    public val state: Flow<DataSourceState<T>>
        get() = flow {
            emit(DataSourceState.Loading)
            emit(await().toDataSourceState())
        }
}

public fun <T : Any> dataSource(load: suspend () -> T): DataSource<T> = SuspendingDataSource(load)

public inline fun <T : Any, R> DataSource<T>.getOrElse(transform: (T) -> R, orElse: () -> R): R =
    getOrNull()?.let(transform) ?: orElse()
