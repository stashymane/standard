package dev.stashy.standard.ui.components.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Stable
import kotlin.math.roundToInt

@Stable
internal class ScrollStateAdapter(
    private val scrollState: ScrollState,
) : ScrollbarState {
    override val value: Double
        get() = scrollState.value.toDouble()
    override val maxValue: Double
        get() = scrollState.maxValue.toDouble()
    override val viewportSize: Int
        get() = scrollState.viewportSize

    override suspend fun scrollTo(value: Double) {
        scrollState.scrollTo(value.roundToInt())
    }
}
