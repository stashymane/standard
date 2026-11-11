package dev.stashy.data

import dev.stashy.data.source.SharedDataSourceImpl
import kotlinx.coroutines.flow.StateFlow

/**
 * A [DataSource] whose value is loaded once and shared by every observer.
 */
public interface SharedDataSource<out T : Any> : DataSource<T> {
    override val state: StateFlow<DataSourceState<T>>
    override fun getOrNull(): T? = state.value.valueOrNull

    /** Emits true while a load is in progress. */
    public val isRefreshing: StateFlow<Boolean>

    /**
     * Forces a reload and returns its result.
     *
     * Every observer of [state] receives the new value or failure.
     */
    public suspend fun refresh(): Result<T>

    /** Ensures the value is loaded. Returns immediately if a value is already cached. */
    public suspend fun preload() {
        await()
    }
}

public fun <T : Any> DataSource<T>.shared(): SharedDataSource<T> =
    SharedDataSourceImpl(this)
