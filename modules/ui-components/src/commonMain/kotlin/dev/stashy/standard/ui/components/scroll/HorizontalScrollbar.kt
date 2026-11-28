package dev.stashy.standard.ui.components.scroll

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
public fun HorizontalScrollbar(
    scrollbarState: ScrollbarState,
    modifier: Modifier = Modifier,
    style: ScrollbarStyle = LocalScrollbarStyle.current ?: ScrollbarStyle.defaults()
) {
    Scrollbar(
        scrollbarState = scrollbarState,
        isVertical = false,
        modifier = modifier,
        style = style,
    )
}

@Composable
public fun HorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    style: ScrollbarStyle = LocalScrollbarStyle.current ?: ScrollbarStyle.defaults()
) {
    HorizontalScrollbar(
        scrollbarState = rememberScrollbarState(scrollState),
        modifier = modifier,
        style = style,
    )
}

@Composable
public fun HorizontalScrollbar(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    style: ScrollbarStyle = LocalScrollbarStyle.current ?: ScrollbarStyle.defaults()
) {
    HorizontalScrollbar(
        scrollbarState = rememberScrollbarState(lazyListState),
        modifier = modifier,
        style = style,
    )
}

@Composable
@Preview
private fun HorizontalScrollbarPreview() {
    val scrollState = rememberScrollState()

    Box(Modifier.fillMaxWidth().height(200.dp)) {
        Box(Modifier.fillMaxWidth().height(200.dp).horizontalScroll(scrollState)) {
            Box(Modifier.width(1000.dp).fillMaxHeight())
        }

        HorizontalScrollbar(
            scrollState,
            Modifier.align(Alignment.BottomCenter).padding(2.dp),
        )
    }
}

@Composable
@Preview
private fun LazyHorizontalScrollbarPreview() {
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxWidth().height(200.dp)) {
        LazyRow(Modifier.fillMaxSize(), state = listState) {
            items(100) { Box(Modifier.width(30.dp).fillMaxHeight()) }
        }

        HorizontalScrollbar(
            listState,
            Modifier.align(Alignment.BottomCenter).padding(2.dp),
        )
    }
}
