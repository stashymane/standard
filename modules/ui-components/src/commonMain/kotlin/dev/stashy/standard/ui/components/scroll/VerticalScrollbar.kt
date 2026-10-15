package dev.stashy.standard.ui.components.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
public fun VerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    collapsedWidth: Dp = ScrollbarThicknessCollapsed,
    expandedWidth: Dp = ScrollbarThicknessExpanded,
) {
    Scrollbar(
        scrollState = scrollState,
        isVertical = true,
        modifier = modifier,
        collapsedThickness = collapsedWidth,
        expandedThickness = expandedWidth,
    )
}

@Composable
@Preview
private fun VerticalScrollbarPreview() {
    val scrollState = rememberScrollState()

    Box(Modifier.fillMaxWidth().height(200.dp)) {
        Box(Modifier.fillMaxWidth().height(200.dp).verticalScroll(scrollState)) {
            Box(Modifier.height(1000.dp))
        }

        VerticalScrollbar(
            scrollState,
            Modifier.align(Alignment.CenterEnd).padding(2.dp),
        )
    }
}
