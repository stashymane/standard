package dev.stashy.data

import dev.stashy.data.source.CachingDataSource

/**
 * A [DataSource] that retains its value after a successful load.
 */
public interface CachedDataSource<out T> : DataSource<T> {
    /** Ensure the value is loaded. Returns immediately if already cached. */
    public suspend fun preload()
}

public fun <T> DataSource<T>.cached(): CachedDataSource<T> = CachingDataSource { await() }
