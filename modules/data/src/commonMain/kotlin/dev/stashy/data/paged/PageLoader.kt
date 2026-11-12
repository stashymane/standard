package dev.stashy.data.paged

/**
 * Loads pages of [T] by their page [Key].
 */
public fun interface PageLoader<Key, T : Any> {
    /**
     * Loads the page at [key], or the first page when [key] is null.
     *
     * @param key the key of the page, usually an ID or record offset. `null` for the first page.
     * @param size the requested limit of items to return
     */
    public suspend fun load(key: Key?, size: Int): Page<T, Key>
}
