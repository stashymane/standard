package dev.stashy.standard.samples.gallery.icons.outlined

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.stashy.standard.samples.gallery.icons.Icons

val Icons.Outlined.ArrowBack24Dp: ImageVector
    get() {
        if (_ArrowBack24Dp != null) {
            return _ArrowBack24Dp!!
        }
        _ArrowBack24Dp = ImageVector.Builder(
            name = "Outlined.ArrowBack24Dp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFFE3E3E3))) {
                moveTo(7.83f, 13f)
                lineToRelative(5.6f, 5.6f)
                lineTo(12f, 20f)
                lineTo(4f, 12f)
                lineToRelative(8f, -8f)
                lineToRelative(1.43f, 1.4f)
                lineTo(7.83f, 11f)
                horizontalLineTo(20f)
                verticalLineToRelative(2f)
                horizontalLineTo(7.83f)
                close()
            }
        }.build()

        return _ArrowBack24Dp!!
    }

@Suppress("ObjectPropertyName")
private var _ArrowBack24Dp: ImageVector? = null
