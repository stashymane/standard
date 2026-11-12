package dev.stashy.data.paged

/**
 * Loads pages by an opaque key.
 *
 * The key is not interpreted by this API, so it can represent an offset, a cursor or any
 * other value understood by the loader.
 */
public fun interface PageLoader<K, T : Any> {
    /**
     * Loads the page at [key], or the first page when [key] is null.
     *
     * [size] is the maximum number of items to request.
     */
    public suspend fun load(key: K?, size: Int): Page<T, K>
}
