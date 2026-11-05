package dev.stashy.standard.exposed.cursor.r2dbc

import dev.stashy.standard.exposed.cursor.cursorAfter
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.andWhere

/**
 * Orders this query by [column] and, when [cursor] is non-null, restricts it to rows strictly
 * after the cursor in [order].
 */
public fun <T : Comparable<T>> Query.applyCursor(
    column: Column<T>,
    cursor: T? = null,
    order: SortOrder = SortOrder.DESC,
): Query {
    require(orderByExpressions.isEmpty()) { "Cursor pagination requires a query without an ORDER BY clause." }

    if (cursor != null) {
        andWhere { column.cursorAfter(cursor, order) }
    }

    orderBy(column, order)

    return this
}
