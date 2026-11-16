package dev.stashy.standard.ui.components.scroll

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal val ScrollbarThicknessCollapsed = 4.dp
internal val ScrollbarThicknessExpanded = 6.dp

private const val MinThumbSizePx = 16

internal fun computeThumb(
    trackSize: Int,
    contentSize: Double,
    viewportSize: Int,
    scrollOffset: Double,
): Pair<Double, Double> {
    val effectiveContentSize = contentSize.takeIf { it > 0.0 } ?: 1.0
    val effectiveViewport = viewportSize.toDouble().coerceAtMost(effectiveContentSize)
    val thumbSize =
        (trackSize * effectiveViewport / effectiveContentSize)
            .coerceAtLeast(MinThumbSizePx.toDouble())
            .coerceAtMost(trackSize.toDouble())
    val scrollRange = (effectiveContentSize - effectiveViewport).coerceAtLeast(1.0)
    val thumbOffset = ((trackSize - thumbSize) * (scrollOffset / scrollRange))
        .coerceIn(0.0, trackSize - thumbSize)
    return thumbSize to thumbOffset
}

@Composable
internal fun Scrollbar(
    scrollbarState: ScrollbarState,
    isVertical: Boolean,
    modifier: Modifier = Modifier,
    collapsedThickness: Dp = ScrollbarThicknessCollapsed,
    expandedThickness: Dp = ScrollbarThicknessExpanded,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val dragMutex = remember { Mutex() }
    val unscrolledDragDistance = remember { mutableStateOf(0.0) }

    val hovered by interactionSource.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging
    val thickness by animateDpAsState(if (active) expandedThickness else collapsedThickness)
    val color by animateColorAsState(
        MaterialTheme.colorScheme.outline.copy(alpha = if (active) 0.4f else 0.2f)
    )

    if (scrollbarState.maxValue <= 0.0) return

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
            .pointerInput(scrollbarState, isVertical) {
                val onDrag: (Float) -> Unit = { dragAmount ->
                    val trackSize = if (isVertical) size.height else size.width
                    val drag = dragAmount.toDouble()
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        dragMutex.withLock {
                            val viewportSize = scrollbarState.viewportSize
                            val maxValue = scrollbarState.maxValue
                            if (maxValue <= 0.0) return@withLock

                            val (thumbSize, thumbOffset) = computeThumb(
                                trackSize,
                                maxValue + viewportSize,
                                viewportSize,
                                scrollbarState.value,
                            )
                            if (trackSize <= thumbSize) return@withLock

                            val thumbRange = trackSize - thumbSize
                            val scrollScale = thumbRange / maxValue
                            val currentPosition = thumbOffset
                            val targetPosition = (currentPosition + drag +
                                unscrolledDragDistance.value)
                                .coerceIn(0.0, thumbRange)
                            val sliderDelta = targetPosition - currentPosition

                            scrollbarState.scrollTo(targetPosition / scrollScale)
                            unscrolledDragDistance.value += drag - sliderDelta
                        }
                    }
                }

                if (isVertical) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = true
                            unscrolledDragDistance.value = 0.0
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { _, dragAmount -> onDrag(dragAmount) }
                } else {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            unscrolledDragDistance.value = 0.0
                        },
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
            val contentSize = scrollbarState.maxValue + scrollbarState.viewportSize
            val (thumbSize, thumbOffset) = computeThumb(
                trackSize,
                contentSize,
                scrollbarState.viewportSize,
                scrollbarState.value,
            )
            val thumbSizePx = thumbSize.roundToInt()
            val thumbOffsetPx = thumbOffset.roundToInt()
            val thumbPlaceable = measurables[0].measure(
                if (isVertical) {
                    constraints.copy(minHeight = thumbSizePx, maxHeight = thumbSizePx)
                } else {
                    constraints.copy(minWidth = thumbSizePx, maxWidth = thumbSizePx)
                }
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                if (isVertical) {
                    thumbPlaceable.placeRelative(
                        constraints.maxWidth - thumbPlaceable.width,
                        thumbOffsetPx,
                    )
                } else {
                    thumbPlaceable.placeRelative(
                        thumbOffsetPx,
                        constraints.maxHeight - thumbPlaceable.height,
                    )
                }
            }
        }
    }
}
