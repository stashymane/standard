package dev.stashy.data.paged

import dev.stashy.data.DataSource
import dev.stashy.data.PagedDataSource

internal class Pager<K, T : Any>(
    private val loader: PageLoader<K, T>,
) : PagedDataSource<K, T> {
    override fun page(size: Int, key: K?): DataSource<Page<T, K>> =
        DataSource { loader.load(key, size) }
}
