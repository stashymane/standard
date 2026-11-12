package dev.stashy.data

import dev.stashy.data.paged.Page
import dev.stashy.data.paged.PageLoader
import dev.stashy.data.paged.Pager
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PagingTest {
    @Test
    fun `first page is loaded with a null key and requested size`() = runTest {
        var receivedKey: String? = "unset"
        var receivedSize = -1
        val pager = Pager<String, Int> { key, size ->
            receivedKey = key
            receivedSize = size
            Page(listOf(1, 2), next = "next")
        }

        val page = pager.page(size = 2).await().getOrThrow()

        assertNull(receivedKey)
        assertEquals(2, receivedSize)
        assertEquals(Page(listOf(1, 2), next = "next"), page)
    }

    @Test
    fun `next page is loaded with the provided key`() = runTest {
        var receivedKey: String? = null
        val pager = Pager<String, Int> { key, _ ->
            receivedKey = key
            Page(listOf(3, 4), next = null)
        }

        val page = pager.page(size = 2, key = "next").await().getOrThrow()

        assertEquals("next", receivedKey)
        assertEquals(Page(listOf(3, 4), next = null), page)
    }

    @Test
    fun `page size is selected for each call`() = runTest {
        val receivedSizes = mutableListOf<Int>()
        val pager = Pager<String, Int> { _, size ->
            receivedSizes += size
            Page(emptyList(), next = null)
        }

        pager.page(size = 1).await()
        pager.page(size = 7, key = "next").await()

        assertEquals(listOf(1, 7), receivedSizes)
    }

    @Test
    fun `offset and cursor keys are both supported`() = runTest {
        val offsetPager = Pager<Int, String> { key, size ->
            val start = key ?: 0
            Page((start until start + size).map { "item$it" }, next = start + size)
        }

        val cursorPager = Pager<String, String> { key, size ->
            val start = key?.removePrefix("cursor")?.toInt() ?: 0
            Page((start until start + size).map { "item$it" }, next = "cursor${start + size}")
        }

        assertEquals(
            Page(listOf("item0", "item1"), next = 2),
            offsetPager.page(size = 2).await().getOrThrow(),
        )
        assertEquals(
            Page(listOf("item2"), next = 3),
            offsetPager.page(size = 1, key = 2).await().getOrThrow(),
        )

        assertEquals(
            Page(listOf("item0", "item1"), next = "cursor2"),
            cursorPager.page(size = 2).await().getOrThrow(),
        )
        assertEquals(
            Page(listOf("item2"), next = "cursor3"),
            cursorPager.page(size = 1, key = "cursor2").await().getOrThrow(),
        )
    }

    @Test
    fun `page sources expose loading and success states`() = runTest {
        val pager = Pager(
            PageLoader<String, Int> { _, _ -> Page(listOf(1), next = null) },
        )

        assertEquals(
            listOf(
                DataSourceState.Loading,
                DataSourceState.Success(Page(listOf(1), next = null)),
            ),
            pager.page(size = 1).state.toList(),
        )
    }

    @Test
    fun `page load failures are returned by await`() = runTest {
        val pager = Pager<String, Int> { _, _ -> error("boom") }

        val result = pager.page(size = 1).await()

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }
}
