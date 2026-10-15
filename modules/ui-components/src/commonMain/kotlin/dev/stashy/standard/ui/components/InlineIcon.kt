package dev.stashy.standard.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * Icon scaled to the current text style's line height.
 */
@Composable
public fun InlineIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
    scale: Float = 0.8f
) {
    val iconSize = LocalTextStyle.current.lineHeight.inDp() * scale

    Icon(imageVector, contentDescription, modifier.size(iconSize), tint)
}

@Composable
private fun TextUnit.inDp(): Dp = with(LocalDensity.current) {
    this@inDp.toDp()
}
