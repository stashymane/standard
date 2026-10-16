package dev.stashy.standard.ui.responsive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window size breakpoints mirroring `WindowSizeClass` V2 lower bounds.
 */
public enum class ContainerSize(
    /** Lower bound for width comparisons, in dp. */
    public val widthDp: Int,
    /** Lower bound for height comparisons, in dp. */
    public val heightDp: Int,
) {
    Compact(0, 0),
    Medium(600, 480),
    Expanded(840, 900),
    Large(1200, 900),
    Desktop(1600, 900),
    ;

    /** [widthDp] as [Dp], useful for layout constraints such as max content width. */
    public val width: Dp get() = widthDp.dp

    /** [heightDp] as [Dp], useful for layout constraints. */
    public val height: Dp get() = heightDp.dp
}
