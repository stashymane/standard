package dev.stashy.standard.samples.gallery.icons.outlined

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.stashy.standard.samples.gallery.icons.Icons

val Icons.Outlined.Star24Dp: ImageVector
    get() {
        if (_Star24Dp != null) {
            return _Star24Dp!!
        }
        _Star24Dp = ImageVector.Builder(
            name = "Outlined.Star24Dp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFFE3E3E3))) {
                moveTo(8.85f, 16.83f)
                lineTo(12f, 14.93f)
                lineToRelative(3.15f, 1.93f)
                lineToRelative(-0.82f, -3.6f)
                lineToRelative(2.78f, -2.4f)
                lineToRelative(-3.65f, -0.33f)
                lineTo(12f, 7.13f)
                lineTo(10.55f, 10.5f)
                lineTo(6.9f, 10.83f)
                lineToRelative(2.78f, 2.43f)
                lineTo(8.85f, 16.83f)
                close()
                moveTo(5.83f, 21f)
                lineToRelative(1.63f, -7.03f)
                lineTo(2f, 9.25f)
                lineTo(9.2f, 8.63f)
                lineTo(12f, 2f)
                lineToRelative(2.8f, 6.63f)
                lineTo(22f, 9.25f)
                lineToRelative(-5.45f, 4.72f)
                lineTo(18.18f, 21f)
                lineTo(12f, 17.27f)
                lineTo(5.83f, 21f)
                close()
                moveTo(12f, 12.25f)
                close()
            }
        }.build()

        return _Star24Dp!!
    }

@Suppress("ObjectPropertyName")
private var _Star24Dp: ImageVector? = null
