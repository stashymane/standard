package dev.stashy.standard.ui.components.scroll

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

internal val ScrollbarThickness = 6.dp

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
    style: ScrollbarStyle = LocalScrollbarStyle.current ?: ScrollbarStyle.defaults()
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val dragMutex = remember { Mutex() }

    val hovered by interactionSource.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging
    val color by animateColorAsState(if (active) style.activeThumbColor else style.thumbColor)
    val gutterColor by animateColorAsState(
        if (hovered) style.gutterColor else Color.Transparent
    )

    if (scrollbarState.maxValue <= 0.0) return

    val trackModifier = if (isVertical) {
        Modifier.width(style.thickness * 2).fillMaxHeight()
    } else {
        Modifier.height(style.thickness * 2).fillMaxWidth()
    }
    val thumbModifier = if (isVertical) Modifier.width(style.thickness) else Modifier.height(style.thickness)

    Box(
        modifier = modifier
            .then(trackModifier)
            .background(gutterColor)
            .hoverable(interactionSource)
            .pointerInput(scrollbarState, isVertical) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        dragging = true

                        val press = (if (isVertical) down.position.y else down.position.x).toDouble()
                        val trackSize = if (isVertical) size.height else size.width
                        val viewportSize = scrollbarState.viewportSize
                        val maxValue = scrollbarState.maxValue
                        val (thumbSize, thumbOffset) = computeThumb(
                            trackSize,
                            maxValue + viewportSize,
                            viewportSize,
                            scrollbarState.value,
                        )
                        val anchor = if (press in thumbOffset..thumbOffset + thumbSize) {
                            press - thumbOffset
                        } else {
                            thumbSize / 2.0
                        }

                        val moveTo: (Float) -> Unit = { position ->
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                dragMutex.withLock {
                                    val viewport = scrollbarState.viewportSize
                                    val max = scrollbarState.maxValue
                                    if (max <= 0.0) return@withLock

                                    val track = if (isVertical) size.height else size.width
                                    val (thumb, _) = computeThumb(
                                        track,
                                        max + viewport,
                                        viewport,
                                        scrollbarState.value,
                                    )
                                    val thumbRange = track - thumb
                                    if (thumbRange <= 0.0) return@withLock

                                    val targetThumbOffset =
                                        (position.toDouble() - anchor).coerceIn(0.0, thumbRange)
                                    scrollbarState.scrollTo(targetThumbOffset / thumbRange * max)
                                }
                            }
                        }

                        moveTo(press.toFloat())

                        var held = true
                        while (held) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.pressed }
                            if (change == null) {
                                held = false
                            } else {
                                change.consume()
                                moveTo(if (isVertical) change.position.y else change.position.x)
                            }
                        }
                        dragging = false
                    }
                }
            }
            .pointerInput(scrollbarState, isVertical) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.fold(Offset.Zero) { acc, change -> acc + change.scrollDelta }
                            val amount = if (isVertical) delta.y else delta.x
                            if (amount != 0f) {
                                event.changes.forEach(PointerInputChange::consume)
                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    dragMutex.withLock {
                                        scrollbarState.scrollTo(scrollbarState.value + amount)
                                    }
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Layout(
            content = {
                Box(
                    thumbModifier
                        .clip(style.shape)
                        .background(color)
                )
            }
        ) { measurables, constraints ->
            val mainAxisMax = if (isVertical) constraints.maxHeight else constraints.maxWidth
            val crossAxisMax = if (isVertical) constraints.maxWidth else constraints.maxHeight
            val mainAxisUnbounded = mainAxisMax == Constraints.Infinity
            val trackSize = if (mainAxisUnbounded) 0 else mainAxisMax
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
            val mainSize = if (mainAxisUnbounded) thumbSizePx else mainAxisMax
            val crossSize = if (crossAxisMax == Constraints.Infinity) {
                if (isVertical) thumbPlaceable.width else thumbPlaceable.height
            } else crossAxisMax
            
            layout(
                if (isVertical) crossSize else mainSize,
                if (isVertical) mainSize else crossSize,
            ) {
                if (isVertical) {
                    thumbPlaceable.placeRelative(
                        (crossSize - thumbPlaceable.width) / 2,
                        thumbOffsetPx,
                    )
                } else {
                    thumbPlaceable.placeRelative(
                        thumbOffsetPx,
                        (crossSize - thumbPlaceable.height) / 2,
                    )
                }
            }
        }
    }
}
