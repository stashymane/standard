package dev.stashy.standard.ui.components.scroll

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import kotlin.math.abs

/**
 * Estimates lazy list content size from the size of currently visible items, as items that
 * haven't been composed yet have no known size.
 */
@Stable
internal class LazyListStateAdapter(
    private val lazyListState: LazyListState,
) : ScrollbarState {
    private val layoutInfo
        get() = lazyListState.layoutInfo

    override val viewportSize: Int
        get() {
            val size = layoutInfo.viewportSize
            return if (layoutInfo.orientation == Orientation.Vertical) size.height else size.width
        }

    override val maxValue: Double
        get() = (contentSize - viewportSize).coerceAtLeast(0.0)

    override val value: Double
        get() {
            val firstVisibleItem = firstVisibleItem() ?: return 0.0
            val offset = firstVisibleItem.index * averageItemSizeWithSpacing - firstVisibleItem.offset
            val visualOffset =
                if (layoutInfo.reverseLayout) contentSize - viewportSize - offset else offset
            return visualOffset.coerceIn(0.0, maxValue)
        }

    override suspend fun scrollTo(value: Double) {
        val target = value.coerceIn(0.0, maxValue)
        val distance = target - this.value

        if (abs(distance) <= viewportSize) {
            // Moving by the exact same amount avoids jumping to an offset other than the requested
            // one, which would otherwise happen when the actual item size differs from the average.
            val delta = if (layoutInfo.reverseLayout) -distance else distance
            lazyListState.scrollBy(delta.toFloat())
        } else {
            snapTo(target)
        }
    }

    private suspend fun snapTo(value: Double) {
        val itemCount = layoutInfo.totalItemsCount
        val itemSize = averageItemSizeWithSpacing
        if (itemCount == 0 || itemSize <= 0.0) return

        val target = value.coerceIn(0.0, maxValue)
        val targetOffset = if (layoutInfo.reverseLayout) contentSize - viewportSize - target else target
        val index = (targetOffset / itemSize).toInt().coerceIn(0, itemCount - 1)
        val offsetInItem = (targetOffset - index * itemSize).toInt().coerceAtLeast(0)
        lazyListState.scrollToItem(index, offsetInItem)
    }

    private class VisibleItem(val index: Int, val offset: Int)

    /**
     * Index into the visible items of the first item that isn't a sticky header currently pinned to
     * the start. Pinned headers always count as visible and would otherwise distort the first
     * visible index and the average item size.
     */
    private fun firstFloatingVisibleItemIndex(): Int? {
        val items = layoutInfo.visibleItemsInfo
        return when (items.size) {
            0 -> null
            1 -> 0
            else -> {
                val first = items[0]
                val second = items[1]
                val isPinned =
                    first.index < second.index - 1 ||
                        first.offset + first.size + layoutInfo.mainAxisItemSpacing > second.offset
                if (isPinned) 1 else 0
            }
        }
    }

    private fun firstVisibleItem(): VisibleItem? {
        val index = firstFloatingVisibleItemIndex() ?: return null
        val item = layoutInfo.visibleItemsInfo[index]
        return VisibleItem(item.index, item.offset)
    }

    private fun averageVisibleItemSize(): Double {
        val firstIndex = firstFloatingVisibleItemIndex() ?: return 0.0
        val items = layoutInfo.visibleItemsInfo
        val first = items[firstIndex]
        val last = items.last()
        val count = items.size - firstIndex
        return (last.offset + last.size - first.offset -
            (count - 1) * layoutInfo.mainAxisItemSpacing).toDouble() / count
    }

    private val averageItemSizeWithSpacing: Double
        get() = averageVisibleItemSize() + layoutInfo.mainAxisItemSpacing

    private val contentSize: Double
        get() {
            val info = layoutInfo
            val itemCount = info.totalItemsCount
            val contentPadding = info.beforeContentPadding + info.afterContentPadding
            return averageVisibleItemSize() * itemCount +
                info.mainAxisItemSpacing * (itemCount - 1).coerceAtLeast(0) +
                contentPadding
        }
}
