package dev.stashy.standard.ui.components.scroll

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import kotlin.math.abs

/**
 * Estimates lazy list scroll metrics from the sizes of composed items, extrapolating
 * not-yet-composed items with a running average of all measured sizes.
 */
@Stable
internal class LazyListStateAdapter(
    private val lazyListState: LazyListState,
) : ScrollbarState {
    private val layoutInfo
        get() = lazyListState.layoutInfo

    private val measuredSizes = HashMap<Int, Int>()
    private var measuredSizeSum = 0L
    private var measuredCount = 0

    private var cachedLayoutInfo: LazyListLayoutInfo? = null
    private var cachedContentSize = 0.0
    private var cachedOffset = 0.0

    private val runningAverageItemSize: Double
        get() = if (measuredCount == 0) 0.0 else measuredSizeSum.toDouble() / measuredCount

    override val viewportSize: Int
        get() {
            val size = layoutInfo.viewportSize
            return if (layoutInfo.orientation == Orientation.Vertical) size.height else size.width
        }

    override val maxValue: Double
        get() {
            refresh()
            return (cachedContentSize - viewportSize).coerceAtLeast(0.0)
        }

    override val value: Double
        get() {
            refresh()
            val visualOffset =
                if (layoutInfo.reverseLayout) cachedContentSize - viewportSize - cachedOffset else cachedOffset
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
        val info = layoutInfo
        val itemCount = info.totalItemsCount
        val itemSize = runningAverageItemSize + info.mainAxisItemSpacing
        if (itemCount == 0 || itemSize <= 0.0) return

        val target = value.coerceIn(0.0, maxValue)
        val targetOffset = if (info.reverseLayout) contentSize - viewportSize - target else target
        val index = ((targetOffset - info.beforeContentPadding) / itemSize).toInt().coerceIn(0, itemCount - 1)
        val offsetInItem = (targetOffset - estimatedOffset(index)).toInt().coerceAtLeast(0)
        lazyListState.scrollToItem(index, offsetInItem)
    }

    private fun refresh() {
        val info = layoutInfo
        if (cachedLayoutInfo === info) return

        for (item in info.visibleItemsInfo) {
            val previous = measuredSizes.put(item.index, item.size)
            if (previous == null) {
                measuredCount++
                measuredSizeSum += item.size
            } else {
                measuredSizeSum += item.size - previous
            }
        }

        cachedContentSize = estimatedOffset(info.totalItemsCount) + info.afterContentPadding
        val first = firstVisibleItem()
        cachedOffset = if (first == null) 0.0 else estimatedOffset(first.index) - first.offset
        cachedLayoutInfo = info
    }

    /**
     * Estimated main-axis offset of the start of the item at [index] in content coordinates:
     * exact for composed items via their measured sizes, extrapolated with the running average
     * for the rest.
     */
    private fun estimatedOffset(index: Int): Double {
        val info = layoutInfo
        var measuredSum = 0
        var measuredBelow = 0
        for ((itemIndex, size) in measuredSizes) {
            if (itemIndex < index) {
                measuredSum += size
                measuredBelow++
            }
        }
        return info.beforeContentPadding +
            measuredSum +
            runningAverageItemSize * (index - measuredBelow) +
            info.mainAxisItemSpacing * index.toDouble()
    }

    private val contentSize: Double
        get() {
            refresh()
            return cachedContentSize
        }

    private class VisibleItem(val index: Int, val offset: Int)

    /**
     * Index into the visible items of the first item that isn't a sticky header currently pinned to
     * the start. Pinned headers always count as visible and would otherwise distort the first
     * visible index.
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
}
