package dev.stashy.data

import dev.stashy.data.paged.Page

/**
 * Provides [DataSource]s of [T] based on their page [Key].
 */
public interface PagedDataSource<Key, T : Any> {
    /**
     * Returns a data source for the page at [key], or the first page when [key] is null.
     * @param size the size of the page
     * @param key the key of the page - usually an ID or record offset. `null` for the first page.
     */
    public fun page(size: Int, key: Key? = null): DataSource<Page<T, Key>>
}
