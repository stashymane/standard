package dev.stashy.standard.ui.modifiers

import androidx.compose.animation.core.EaseOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.stashy.standard.ui.components.scroll.VerticalScrollbar
import dev.stashy.standard.ui.gradients.easedVerticalGradient
import dev.stashy.standard.ui.gradients.stop
import dev.stashy.standard.ui.gradients.toEasingFunction

/** Amount of content hidden by scrolling, in pixels, above and below the viewport. */
private data class HiddenScroll(
    val top: Float,
    val bottom: Float,
)

public object ScrollShadowDefaults {
    public inline val height: Dp
        get() = 32.dp

    @get:Composable
    public inline val color: Color
        get() = MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)
}

@Composable
private fun rememberScrollShadowGradient(color: Color, height: Float): Brush = remember(color, height) {
    Brush.easedVerticalGradient(endY = height) {
        easing = EaseOut.toEasingFunction()
        stop(color)
        stop(color.copy(alpha = 0f))
    }
}

@Composable
public fun Modifier.verticalScrollShadow(
    state: LazyListState,
    height: Dp = ScrollShadowDefaults.height,
    color: Color = ScrollShadowDefaults.color,
): Modifier {
    val heightPx = with(LocalDensity.current) { height.toPx() }
    val gradient = rememberScrollShadowGradient(color, heightPx)

    return drawWithContent {
        drawContent()

        drawScrollShadows(state.hiddenScroll(heightPx), heightPx, gradient)
    }
}

@Composable
public fun Modifier.verticalScrollShadow(
    state: ScrollState,
    height: Dp = ScrollShadowDefaults.height,
    color: Color = ScrollShadowDefaults.color,
): Modifier {
    val heightPx = with(LocalDensity.current) { height.toPx() }
    val gradient = rememberScrollShadowGradient(color, heightPx)

    return drawWithContent {
        drawContent()

        drawScrollShadows(state.hiddenScroll(), heightPx, gradient)
    }
}

private fun LazyListState.hiddenScroll(height: Float): HiddenScroll {
    val top = if (firstVisibleItemIndex > 0) {
        height
    } else {
        firstVisibleItemScrollOffset.toFloat()
    }

    val layoutInfo = layoutInfo
    val lastItem = layoutInfo.visibleItemsInfo.lastOrNull()
    val bottom = when {
        lastItem == null -> 0f
        lastItem.index < layoutInfo.totalItemsCount - 1 -> height
        else -> (
                lastItem.offset + lastItem.size +
                        layoutInfo.afterContentPadding -
                        layoutInfo.viewportEndOffset
                ).toFloat().coerceAtLeast(0f)
    }

    return HiddenScroll(top, bottom)
}

private fun ScrollState.hiddenScroll(): HiddenScroll = HiddenScroll(
    top = value.toFloat(),
    bottom = (maxValue - value).toFloat(),
)

private fun DrawScope.drawScrollShadows(
    hidden: HiddenScroll,
    height: Float,
    gradient: Brush,
) {
    drawScrollShadow(hidden.top, height, gradient, flipped = false)
    drawScrollShadow(hidden.bottom, height, gradient, flipped = true)
}

private fun DrawScope.drawScrollShadow(
    hidden: Float,
    height: Float,
    gradient: Brush,
    flipped: Boolean,
) {
    val fraction = (hidden / height).coerceIn(0f, 1f)
    if (fraction <= 0f) return

    val rect = Size(size.width, height)

    scale(scaleX = 1f, scaleY = if (flipped) -1f else 1f) {
        drawRect(gradient, Offset.Zero, rect, alpha = fraction)
    }
}

private class ScrollShadowPreviewParameters : PreviewParameterProvider<Int> {
    override val values: Sequence<Int> = sequenceOf(0, 100, Int.MAX_VALUE)
}

@Composable
@Preview
private fun VerticalScrollShadowPreview(
    @PreviewParameter(ScrollShadowPreviewParameters::class) offset: Int,
) = Surface {
    val scrollState = rememberScrollState(initial = offset)

    Box(Modifier.fillMaxWidth().height(200.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScrollShadow(scrollState)
                .verticalScroll(scrollState)
                .padding(12.dp)
                .padding(end = 8.dp),
        ) {
            repeat(20) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .padding(2.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }

        VerticalScrollbar(scrollState, Modifier.align(Alignment.CenterEnd))
    }
}

