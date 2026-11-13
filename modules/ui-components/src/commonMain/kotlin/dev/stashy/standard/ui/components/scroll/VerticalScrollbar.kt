package dev.stashy.standard.ui.components.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
    scrollbarState: ScrollbarState,
    modifier: Modifier = Modifier,
    collapsedWidth: Dp = ScrollbarThicknessCollapsed,
    expandedWidth: Dp = ScrollbarThicknessExpanded,
) {
    Scrollbar(
        scrollbarState = scrollbarState,
        isVertical = true,
        modifier = modifier,
        collapsedThickness = collapsedWidth,
        expandedThickness = expandedWidth,
    )
}

@Composable
public fun VerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    collapsedWidth: Dp = ScrollbarThicknessCollapsed,
    expandedWidth: Dp = ScrollbarThicknessExpanded,
) {
    VerticalScrollbar(
        scrollbarState = rememberScrollbarState(scrollState),
        modifier = modifier,
        collapsedWidth = collapsedWidth,
        expandedWidth = expandedWidth,
    )
}

@Composable
public fun VerticalScrollbar(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    collapsedWidth: Dp = ScrollbarThicknessCollapsed,
    expandedWidth: Dp = ScrollbarThicknessExpanded,
) {
    VerticalScrollbar(
        scrollbarState = rememberScrollbarState(lazyListState),
        modifier = modifier,
        collapsedWidth = collapsedWidth,
        expandedWidth = expandedWidth,
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

@Composable
@Preview
private fun LazyVerticalScrollbarPreview() {
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxWidth().height(200.dp)) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(100) { Box(Modifier.fillMaxWidth().height(30.dp)) }
        }

        VerticalScrollbar(
            listState,
            Modifier.align(Alignment.CenterEnd).padding(2.dp),
        )
    }
}
