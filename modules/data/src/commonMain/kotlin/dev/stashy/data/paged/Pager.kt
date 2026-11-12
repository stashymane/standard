package dev.stashy.data.paged

import dev.stashy.data.DataSource
import dev.stashy.data.dataSource

/**
 * Produces [dev.stashy.data.DataSource]s for individual pages.
 */
public class Pager<K, T : Any>(
    private val loader: PageLoader<K, T>,
) {
    /** Creates a pager that loads pages with [load]. */
    public constructor(load: suspend (key: K?, size: Int) -> Page<T, K>) : this(PageLoader(load))

    /**
     * Returns a data source for the page at [key], or the first page when [key] is null.
     *
     * [size] is the maximum number of items to request.
     */
    public fun page(size: Int, key: K? = null): DataSource<Page<T, K>> =
        dataSource { loader.load(key, size) }
}
