package dev.stashy.standard.ui.gradients

import androidx.compose.ui.graphics.Color
import com.github.ajalt.colormath.extensions.android.composecolor.toColormathColor
import com.github.ajalt.colormath.transform.InterpolatorBuilder
import com.github.ajalt.colormath.transform.InterpolatorStopBuilderContext

public fun InterpolatorBuilder.stop(
    color: Color,
    builder: InterpolatorStopBuilderContext = {},
): Unit = stop(color.toColormathColor(), builder)

public fun InterpolatorBuilder.stop(
    color: Color,
    position: Float,
    builder: InterpolatorStopBuilderContext = {},
): Unit = stop(color.toColormathColor(), position, builder)

public fun InterpolatorBuilder.stop(
    color: Color,
    position: Double,
    builder: InterpolatorStopBuilderContext = {},
): Unit = stop(color.toColormathColor(), position, builder)
