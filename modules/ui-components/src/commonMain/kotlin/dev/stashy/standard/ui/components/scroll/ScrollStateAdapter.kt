package dev.stashy.standard.ui.components.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Stable

@Stable
internal class ScrollStateAdapter(
    private val scrollState: ScrollState,
) : ScrollbarState {
    override val value: Int
        get() = scrollState.value
    override val maxValue: Int
        get() = scrollState.maxValue
    override val viewportSize: Int
        get() = scrollState.viewportSize

    override suspend fun scrollTo(value: Int) {
        scrollState.scrollTo(value)
    }
}
