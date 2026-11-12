package dev.stashy.data.paged

/**
 * A page of items and the opaque key used to request the page after it.
 */
public data class Page<out T, out K>(
    public val items: List<T>,
    /** Key for the next page, or null when this is the last page. */
    public val next: K?,
)
