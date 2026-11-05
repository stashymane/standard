package dev.stashy.standard.exposed.cursor

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less

/**
 * Returns a predicate matching rows strictly after [cursor] when ordered by this column in [order].
 *
 * The column must be non-null. For stable pagination the column values must be unique.
 */
public fun <T : Comparable<T>> Column<T>.cursorAfter(
    cursor: T,
    order: SortOrder,
): Op<Boolean> {
    require(!columnType.nullable) { "Cursor pagination does not support nullable columns." }

    return when (order) {
        SortOrder.ASC,
        SortOrder.ASC_NULLS_FIRST,
        SortOrder.ASC_NULLS_LAST -> this greater cursor

        SortOrder.DESC,
        SortOrder.DESC_NULLS_FIRST,
        SortOrder.DESC_NULLS_LAST -> this less cursor
    }
}
