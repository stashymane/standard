package dev.stashy.standard.ui.components.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/** Supplies a scrollbar with the scroll metrics of an arbitrary scrollable component. */
@Stable
public interface ScrollbarState {
    /** Current scroll offset, in the same units accepted by [scrollTo]. */
    public val value: Int

    /** Maximum offset [value] can reach. */
    public val maxValue: Int

    /** Size of the visible part of the content along the scroll axis. */
    public val viewportSize: Int

    /** Scrolls to the given offset, clamped to `0..maxValue`. */
    public suspend fun scrollTo(value: Int)
}

@Composable
public fun rememberScrollbarState(scrollState: ScrollState): ScrollbarState =
    remember(scrollState) { ScrollStateAdapter(scrollState) }

@Composable
public fun rememberScrollbarState(lazyListState: LazyListState): ScrollbarState =
    remember(lazyListState) { LazyListStateAdapter(lazyListState) }
