package dev.stashy.standard.ui.components.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
public fun VerticalScrollbar(
    scrollbarState: ScrollbarState,
    modifier: Modifier = Modifier,
    style: ScrollbarStyle = LocalScrollbarStyle.current ?: ScrollbarStyle.defaults()
) {
    Scrollbar(
        scrollbarState = scrollbarState,
        isVertical = true,
        modifier = modifier,
        style = style,
    )
}

@Composable
public fun VerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    style: ScrollbarStyle = LocalScrollbarStyle.current ?: ScrollbarStyle.defaults()
) {
    VerticalScrollbar(
        scrollbarState = rememberScrollbarState(scrollState),
        modifier = modifier,
        style = style,
    )
}

@Composable
public fun VerticalScrollbar(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    style: ScrollbarStyle = LocalScrollbarStyle.current ?: ScrollbarStyle.defaults()
) {
    VerticalScrollbar(
        scrollbarState = rememberScrollbarState(lazyListState),
        modifier = modifier,
        style = style,
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
