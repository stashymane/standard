package dev.stashy.standard.ui.components.scroll

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
public data class ScrollbarStyle(
    public val thumbColor: Color,
    public val activeThumbColor: Color,
    public val gutterColor: Color,
    public val thickness: Dp,
    public val shape: Shape,
) {
    public companion object {
        @Composable
        public fun defaults(
            thumbColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            activeThumbColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            gutterColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
            thickness: Dp = ScrollbarThickness,
            shape: Shape = RoundedCornerShape(50),
        ): ScrollbarStyle = ScrollbarStyle(
            thumbColor = thumbColor,
            activeThumbColor = activeThumbColor,
            gutterColor = gutterColor,
            thickness = thickness,
            shape = shape,
        )
    }
}

public val LocalScrollbarStyle: ProvidableCompositionLocal<ScrollbarStyle?> =
    compositionLocalOf { null }

@Preview
@Composable
private fun ScrollbarStylePreview() {
    val style = ScrollbarStyle.defaults(
        thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        activeThumbColor = MaterialTheme.colorScheme.primary,
        gutterColor = MaterialTheme.colorScheme.primaryContainer,
        thickness = 10.dp,
        shape = RoundedCornerShape(4.dp),
    )

    CompositionLocalProvider(LocalScrollbarStyle provides style) {
        val listState = rememberLazyListState()

        Box(Modifier.fillMaxWidth().height(200.dp)) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(100) { Box(Modifier.fillMaxWidth().height(30.dp)) }
            }
            VerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd))
        }
    }
}
