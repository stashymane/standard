package dev.stashy.standard.exposed.cursor.testing

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.SortOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

abstract class CursorPagingTest {
    protected abstract suspend fun reset()

    protected abstract suspend fun insert(id: Int, createdAt: Instant, group: String)

    protected abstract suspend fun page(
        cursor: Int?,
        order: SortOrder,
        limit: Int,
        group: String?,
    ): List<Int>

    @Test
    fun `first page starts at the requested end`() = pagingTest {
        insertRows(5)

        assertEquals(listOf(5, 4), page(null, SortOrder.DESC, 2, null))
        assertEquals(listOf(1, 2), page(null, SortOrder.ASC, 2, null))
    }

    @Test
    fun `cursor continues after the last returned value`() = pagingTest {
        insertRows(5)

        assertEquals(listOf(2, 1), page(3, SortOrder.DESC, 2, null))
        assertEquals(listOf(3, 4), page(2, SortOrder.ASC, 2, null))
    }

    @Test
    fun `filters are preserved`() = pagingTest {
        insert(1, cursorTimestamp(1), "first")
        insert(2, cursorTimestamp(2), "second")
        insert(3, cursorTimestamp(3), "first")

        assertEquals(listOf(3, 1), page(null, SortOrder.DESC, 10, "first"))
    }

    @Test
    fun `empty table returns an empty page`() = pagingTest {
        assertEquals(emptyList(), page(null, SortOrder.DESC, 10, null))
    }

    private fun pagingTest(block: suspend () -> Unit) = runTest {
        reset()
        block()
    }

    private suspend fun insertRows(count: Int) {
        repeat(count) { index ->
            val id = index + 1
            insert(id, cursorTimestamp(id), "all")
        }
    }
}
