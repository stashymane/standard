package dev.stashy.standard.ui.responsive

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.toSize
import kotlin.jvm.JvmInline
import kotlin.math.roundToInt

/**
 * Current window dimensions for responsive layout decisions.
 */
@Immutable
public data class ResponsiveInfo(
    public val width: WidthSize,
    public val height: HeightSize,
) {
    /**
     * Compares both [width] and [height] to [other], returning the tighter (minimum) result.
     *
     * `this >= other` is equivalent to `width >= other && height >= other`.
     */
    public operator fun compareTo(other: ContainerSize): Int =
        minOf(width.compareTo(other), height.compareTo(other))
}

/** Window width in dp; compares against [ContainerSize.widthDp]. */
@JvmInline
public value class WidthSize(public val valueDp: Int) {
    public operator fun compareTo(other: ContainerSize): Int =
        valueDp.compareTo(other.widthDp)
}

/** Window height in dp; compares against [ContainerSize.heightDp]. */
@JvmInline
public value class HeightSize(public val valueDp: Int) {
    public operator fun compareTo(other: ContainerSize): Int =
        valueDp.compareTo(other.heightDp)
}

/**
 * Provides [ResponsiveInfo] to the composition tree.
 *
 * Defaults to a zero-size window (behaves as [ContainerSize.Compact]). Prefer providing
 * a value from [rememberResponsiveInfo] at the app root.
 */
public val LocalResponsiveInfo: ProvidableCompositionLocal<ResponsiveInfo> = compositionLocalOf {
    ResponsiveInfo(width = WidthSize(0), height = HeightSize(0))
}

/**
 * Remembers [ResponsiveInfo] for the current window size from [LocalWindowInfo].
 */
@Composable
public fun rememberResponsiveInfo(): ResponsiveInfo {
    val windowSize = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.toSize().toDpSize()
    }
    return remember(windowSize) {
        ResponsiveInfo(
            width = WidthSize(windowSize.width.value.roundToInt()),
            height = HeightSize(windowSize.height.value.roundToInt()),
        )
    }
}
