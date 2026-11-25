package dev.stashy.standard.samples.gallery.icons.outlined

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.stashy.standard.samples.gallery.icons.Icons

val Icons.Outlined.DarkMode24Dp: ImageVector
    get() {
        if (_DarkMode24Dp != null) {
            return _DarkMode24Dp!!
        }
        _DarkMode24Dp = ImageVector.Builder(
            name = "Outlined.DarkMode24Dp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFFE3E3E3))) {
                moveTo(12f, 21f)
                quadToRelative(-3.75f, 0f, -6.38f, -2.63f)
                reflectiveQuadTo(3f, 12f)
                reflectiveQuadTo(5.63f, 5.63f)
                reflectiveQuadTo(12f, 3f)
                quadToRelative(0.35f, 0f, 0.69f, 0.02f)
                quadToRelative(0.34f, 0.02f, 0.66f, 0.07f)
                quadToRelative(-1.03f, 0.73f, -1.64f, 1.89f)
                reflectiveQuadTo(11.1f, 7.5f)
                quadToRelative(0f, 2.25f, 1.57f, 3.82f)
                reflectiveQuadTo(16.5f, 12.9f)
                quadToRelative(1.38f, 0f, 2.53f, -0.61f)
                reflectiveQuadToRelative(1.88f, -1.64f)
                quadToRelative(0.05f, 0.33f, 0.07f, 0.66f)
                reflectiveQuadTo(21f, 12f)
                quadToRelative(0f, 3.75f, -2.63f, 6.38f)
                reflectiveQuadTo(12f, 21f)
                close()
                moveTo(12f, 19f)
                quadToRelative(2.2f, 0f, 3.95f, -1.21f)
                reflectiveQuadToRelative(2.55f, -3.16f)
                quadToRelative(-0.5f, 0.13f, -1f, 0.2f)
                reflectiveQuadToRelative(-1f, 0.08f)
                quadToRelative(-3.07f, 0f, -5.24f, -2.16f)
                quadTo(9.1f, 10.58f, 9.1f, 7.5f)
                quadToRelative(0f, -0.5f, 0.08f, -1f)
                reflectiveQuadToRelative(0.2f, -1f)
                quadTo(7.43f, 6.3f, 6.21f, 8.05f)
                reflectiveQuadTo(5f, 12f)
                quadToRelative(0f, 2.9f, 2.05f, 4.95f)
                reflectiveQuadTo(12f, 19f)
                close()
                moveTo(11.75f, 12.25f)
                close()
            }
        }.build()

        return _DarkMode24Dp!!
    }

@Suppress("ObjectPropertyName")
private var _DarkMode24Dp: ImageVector? = null
