package dev.stashy.standard.ui.gradients

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import com.github.ajalt.colormath.extensions.android.composecolor.toComposeColor
import com.github.ajalt.colormath.model.Oklab
import com.github.ajalt.colormath.transform.InterpolatorBuilder
import com.github.ajalt.colormath.transform.interpolator
import com.github.ajalt.colormath.transform.sequence

public object GradientDefaults {
    public const val STEPS: Int = 20
}

/**
 * Samples an Oklab color interpolator into Compose [Color]s in [colorSpace].
 */
public fun easeColors(
    steps: Int = GradientDefaults.STEPS,
    colorSpace: ColorSpace = ColorSpaces.Srgb,
    builder: InterpolatorBuilder.() -> Unit,
): List<Color> =
    Oklab.interpolator(builder)
        .sequence(steps)
        .map { it.toComposeColor().convert(colorSpace) }
        .toList()
