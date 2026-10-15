package dev.stashy.standard.ui.components.scroll

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal val ScrollbarThicknessCollapsed = 4.dp
internal val ScrollbarThicknessExpanded = 6.dp

private const val MinThumbSizePx = 16

internal fun computeThumb(
    trackSize: Int,
    contentSize: Int,
    viewportSize: Int,
    scrollOffset: Int,
): Pair<Int, Int> {
    val effectiveContentSize = contentSize.takeIf { it > 0 } ?: 1
    val effectiveViewport = viewportSize.coerceAtMost(effectiveContentSize)
    val thumbSize =
        (trackSize * effectiveViewport.toFloat() / effectiveContentSize).toInt()
            .coerceAtLeast(MinThumbSizePx)
    val scrollRange = (effectiveContentSize - effectiveViewport).coerceAtLeast(1)
    val thumbOffset = ((trackSize - thumbSize) * (scrollOffset.toFloat() / scrollRange))
        .toInt().coerceIn(0, trackSize - thumbSize)
    return thumbSize to thumbOffset
}

@Composable
internal fun Scrollbar(
    scrollState: ScrollState,
    isVertical: Boolean,
    modifier: Modifier = Modifier,
    collapsedThickness: Dp = ScrollbarThicknessCollapsed,
    expandedThickness: Dp = ScrollbarThicknessExpanded,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()

    val hovered by interactionSource.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging
    val thickness by animateDpAsState(if (active) expandedThickness else collapsedThickness)
    val color by animateColorAsState(
        MaterialTheme.colorScheme.outline.copy(alpha = if (active) 0.4f else 0.2f)
    )

    if (scrollState.maxValue <= 0) return

    val trackModifier = if (isVertical) {
        Modifier.width(expandedThickness).fillMaxHeight()
    } else {
        Modifier.height(expandedThickness).fillMaxWidth()
    }
    val contentAlignment = if (isVertical) Alignment.CenterEnd else Alignment.BottomCenter
    val thumbModifier = if (isVertical) Modifier.width(thickness) else Modifier.height(thickness)

    Box(
        modifier = modifier
            .then(trackModifier)
            .hoverable(interactionSource)
            .pointerInput(scrollState, isVertical) {
                val onDrag: (Float) -> Unit = { dragAmount ->
                    val trackSize = if (isVertical) size.height else size.width
                    val contentSize = scrollState.maxValue + scrollState.viewportSize
                    val (thumbSize, _) = computeThumb(
                        trackSize,
                        contentSize,
                        scrollState.viewportSize,
                        scrollState.value,
                    )
                    val effectiveContentSize = contentSize.takeIf { it > 0 } ?: 1
                    val effectiveViewport =
                        scrollState.viewportSize.coerceAtMost(effectiveContentSize)
                    val scrollRange = (effectiveContentSize - effectiveViewport).coerceAtLeast(1)
                    val scrollDelta =
                        dragAmount / (trackSize - thumbSize).coerceAtLeast(1) * scrollRange
                    scope.launch {
                        scrollState.scrollTo((scrollState.value + scrollDelta).toInt())
                    }
                }

                if (isVertical) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { _, dragAmount -> onDrag(dragAmount) }
                } else {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { _, dragAmount -> onDrag(dragAmount) }
                }
            },
        contentAlignment = contentAlignment,
    ) {
        Layout(
            content = {
                Box(
                    thumbModifier
                        .clip(RoundedCornerShape(50))
                        .background(color)
                )
            }
        ) { measurables, constraints ->
            val trackSize =
                if (isVertical) constraints.maxHeight else constraints.maxWidth
            val contentSize = scrollState.maxValue + scrollState.viewportSize
            val (thumbSize, thumbOffset) = computeThumb(
                trackSize,
                contentSize,
                scrollState.viewportSize,
                scrollState.value,
            )
            val thumbPlaceable = measurables[0].measure(
                if (isVertical) {
                    constraints.copy(minHeight = thumbSize, maxHeight = thumbSize)
                } else {
                    constraints.copy(minWidth = thumbSize, maxWidth = thumbSize)
                }
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                if (isVertical) {
                    thumbPlaceable.placeRelative(
                        constraints.maxWidth - thumbPlaceable.width,
                        thumbOffset,
                    )
                } else {
                    thumbPlaceable.placeRelative(
                        thumbOffset,
                        constraints.maxHeight - thumbPlaceable.height,
                    )
                }
            }
        }
    }
}
