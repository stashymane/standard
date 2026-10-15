package dev.stashy.standard.ui.gradients

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.github.ajalt.colormath.transform.InterpolatorBuilder

public fun Brush.Companion.easedLinearGradient(
    start: Offset = Offset.Zero,
    end: Offset = Offset.Infinite,
    tileMode: TileMode = TileMode.Clamp,
    steps: Int = GradientDefaults.STEPS,
    colorSpace: ColorSpace = ColorSpaces.Srgb,
    builder: InterpolatorBuilder.() -> Unit,
): Brush =
    Brush.linearGradient(
        colors = easeColors(steps, colorSpace, builder),
        start = start,
        end = end,
        tileMode = tileMode,
    )

public fun Brush.Companion.easedHorizontalGradient(
    startX: Float = 0.0f,
    endX: Float = Float.POSITIVE_INFINITY,
    tileMode: TileMode = TileMode.Clamp,
    steps: Int = GradientDefaults.STEPS,
    colorSpace: ColorSpace = ColorSpaces.Srgb,
    builder: InterpolatorBuilder.() -> Unit,
): Brush =
    Brush.horizontalGradient(
        colors = easeColors(steps, colorSpace, builder),
        startX = startX,
        endX = endX,
        tileMode = tileMode,
    )

public fun Brush.Companion.easedVerticalGradient(
    startY: Float = 0.0f,
    endY: Float = Float.POSITIVE_INFINITY,
    tileMode: TileMode = TileMode.Clamp,
    steps: Int = GradientDefaults.STEPS,
    colorSpace: ColorSpace = ColorSpaces.Srgb,
    builder: InterpolatorBuilder.() -> Unit,
): Brush =
    Brush.verticalGradient(
        colors = easeColors(steps, colorSpace, builder),
        startY = startY,
        endY = endY,
        tileMode = tileMode,
    )

public fun Brush.Companion.easedRadialGradient(
    center: Offset = Offset.Unspecified,
    radius: Float = Float.POSITIVE_INFINITY,
    tileMode: TileMode = TileMode.Clamp,
    steps: Int = GradientDefaults.STEPS,
    colorSpace: ColorSpace = ColorSpaces.Srgb,
    builder: InterpolatorBuilder.() -> Unit,
): Brush =
    Brush.radialGradient(
        colors = easeColors(steps, colorSpace, builder),
        center = center,
        radius = radius,
        tileMode = tileMode,
    )

public fun Brush.Companion.easedSweepGradient(
    center: Offset = Offset.Unspecified,
    steps: Int = GradientDefaults.STEPS,
    colorSpace: ColorSpace = ColorSpaces.Srgb,
    builder: InterpolatorBuilder.() -> Unit,
): Brush =
    Brush.sweepGradient(
        colors = easeColors(steps, colorSpace, builder),
        center = center,
    )
