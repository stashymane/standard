package dev.stashy.standard.ui.gradients

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpace
import com.github.ajalt.colormath.transform.InterpolatorBuilder
import kotlin.math.hypot

/**
 * Provides a radial gradient that covers the entire surface.
 *
 * @param colors the colors of the gradient.
 * @param size the size of the surface.
 * @param scale the scale of the gradient.
 * @param x normalized horizontal coordinate.
 * @param y normalized vertical coordinate.
 */
public fun Brush.Companion.fullRadialGradient(
    colors: List<Color>,
    size: Size,
    scale: Float = 1f,
    x: Float = 0.5f,
    y: Float = 0.5f,
): Brush = Brush.radialGradient(
    colors,
    center = Offset(size.width * x, size.height * y),
    radius = hypot(size.width * scale, size.height * scale),
)

/**
 * Provides a radial gradient that covers the entire surface.
 *
 * @param size the size of the surface.
 * @param scale the scale of the gradient.
 * @param x normalized horizontal coordinate.
 * @param y normalized vertical coordinate.
 * @param steps how many samples to take along the gradient before converting colors
 * @param colorSpace the target color space
 */
public fun Brush.Companion.fullRadialGradient(
    size: Size,
    scale: Float = 1f,
    x: Float = 0.5f,
    y: Float = 0.5f,
    steps: Int = GradientDefaults.STEPS,
    colorSpace: ColorSpace = GradientDefaults.colorSpace,
    builder: InterpolatorBuilder.() -> Unit,
): Brush = Brush.fullRadialGradient(
    colors = easeColors(steps, colorSpace, builder),
    size = size,
    scale = scale,
    x = x,
    y = y,
)
