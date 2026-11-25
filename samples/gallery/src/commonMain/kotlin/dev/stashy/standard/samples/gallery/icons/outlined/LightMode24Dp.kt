package dev.stashy.standard.samples.gallery.icons.outlined

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.stashy.standard.samples.gallery.icons.Icons

val Icons.Outlined.LightMode24Dp: ImageVector
    get() {
        if (_LightMode24Dp != null) {
            return _LightMode24Dp!!
        }
        _LightMode24Dp = ImageVector.Builder(
            name = "Outlined.LightMode24Dp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFFE3E3E3))) {
                moveTo(14.13f, 14.13f)
                quadTo(15f, 13.25f, 15f, 12f)
                reflectiveQuadTo(14.13f, 9.88f)
                reflectiveQuadTo(12f, 9f)
                reflectiveQuadTo(9.88f, 9.88f)
                reflectiveQuadTo(9f, 12f)
                reflectiveQuadToRelative(0.88f, 2.13f)
                reflectiveQuadTo(12f, 15f)
                reflectiveQuadToRelative(2.13f, -0.88f)
                close()
                moveTo(8.46f, 15.54f)
                quadTo(7f, 14.08f, 7f, 12f)
                quadTo(7f, 9.92f, 8.46f, 8.46f)
                reflectiveQuadTo(12f, 7f)
                reflectiveQuadToRelative(3.54f, 1.46f)
                reflectiveQuadTo(17f, 12f)
                reflectiveQuadToRelative(-1.46f, 3.54f)
                reflectiveQuadTo(12f, 17f)
                quadToRelative(-2.07f, 0f, -3.54f, -1.46f)
                close()
                moveTo(5f, 13f)
                lineTo(1f, 13f)
                lineTo(1f, 11f)
                lineTo(5f, 11f)
                verticalLineToRelative(2f)
                close()
                moveTo(23f, 13f)
                lineTo(19f, 13f)
                lineTo(19f, 11f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(2f)
                close()
                moveTo(11f, 5f)
                lineTo(11f, 1f)
                horizontalLineToRelative(2f)
                lineTo(13f, 5f)
                lineTo(11f, 5f)
                close()
                moveTo(11f, 23f)
                lineTo(11f, 19f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(4f)
                lineTo(11f, 23f)
                close()
                moveTo(6.4f, 7.75f)
                lineTo(3.88f, 5.32f)
                lineTo(5.3f, 3.85f)
                lineToRelative(2.4f, 2.5f)
                lineTo(6.4f, 7.75f)
                close()
                moveTo(18.7f, 20.15f)
                lineToRelative(-2.42f, -2.52f)
                lineToRelative(1.32f, -1.38f)
                lineToRelative(2.52f, 2.43f)
                lineTo(18.7f, 20.15f)
                close()
                moveTo(16.25f, 6.4f)
                lineToRelative(2.43f, -2.52f)
                lineTo(20.15f, 5.3f)
                lineToRelative(-2.5f, 2.4f)
                lineToRelative(-1.4f, -1.3f)
                close()
                moveTo(3.85f, 18.7f)
                lineToRelative(2.53f, -2.43f)
                lineTo(7.75f, 17.6f)
                lineTo(5.33f, 20.13f)
                lineTo(3.85f, 18.7f)
                close()
                moveTo(12f, 12f)
                close()
            }
        }.build()

        return _LightMode24Dp!!
    }

@Suppress("ObjectPropertyName")
private var _LightMode24Dp: ImageVector? = null
