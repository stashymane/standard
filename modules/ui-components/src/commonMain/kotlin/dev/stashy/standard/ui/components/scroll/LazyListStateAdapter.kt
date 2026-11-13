package dev.stashy.standard.ui.components.scroll

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable

/**
 * Estimates lazy list content size from the average size of currently visible items, as items that
 * haven't been composed yet have no known size.
 */
@Stable
internal class LazyListStateAdapter(
    private val lazyListState: LazyListState,
) : ScrollbarState {
    private val layoutInfo
        get() = lazyListState.layoutInfo

    private val averageItemSize: Int
        get() {
            val items = layoutInfo.visibleItemsInfo
            if (items.isEmpty()) return 0
            return items.sumOf { it.size } / items.size + layoutInfo.mainAxisItemSpacing
        }

    private val contentSize: Int
        get() {
            val info = layoutInfo
            val contentPadding = info.beforeContentPadding + info.afterContentPadding
            val itemSize = averageItemSize
            if (info.totalItemsCount == 0 || itemSize <= 0) return contentPadding

            val contentWithoutSpacing = (itemSize - info.mainAxisItemSpacing) * info.totalItemsCount
            val totalSpacing = (info.totalItemsCount - 1) * info.mainAxisItemSpacing
            return contentWithoutSpacing + totalSpacing + contentPadding
        }

    override val value: Int
        get() {
            if (averageItemSize <= 0) return 0
            val offset = averageItemSize * lazyListState.firstVisibleItemIndex +
                    lazyListState.firstVisibleItemScrollOffset
            return if (layoutInfo.reverseLayout) (maxValue - offset).coerceAtLeast(0) else offset
        }

    override val maxValue: Int
        get() = (contentSize - viewportSize).coerceAtLeast(0)

    override val viewportSize: Int
        get() {
            val size = layoutInfo.viewportSize
            return if (layoutInfo.orientation == Orientation.Vertical) size.height else size.width
        }

    override suspend fun scrollTo(value: Int) {
        val itemCount = layoutInfo.totalItemsCount
        val itemSize = averageItemSize
        if (itemCount == 0 || itemSize <= 0) return

        val target = value.coerceIn(0, maxValue)
        val targetOffset =
            if (layoutInfo.reverseLayout) maxValue - target else target
        val index = (targetOffset / itemSize).coerceIn(0, itemCount - 1)
        val offsetInItem = targetOffset - index * itemSize
        lazyListState.scrollToItem(index, offsetInItem.coerceAtLeast(0))
    }
}
