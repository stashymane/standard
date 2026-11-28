package dev.stashy.standard.samples.gallery.screens.scrollbar.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.stashy.standard.samples.gallery.components.SampleItem
import dev.stashy.standard.samples.gallery.components.Section
import dev.stashy.standard.samples.gallery.theme.GalleryTheme
import dev.stashy.standard.ui.components.scroll.VerticalScrollbar

@Composable
internal fun VerticalLazyListSection() {
    Section("Vertical · LazyListState") {
        val state = rememberLazyListState()

        Box(Modifier.height(260.dp).align(Alignment.CenterHorizontally)) {
            LazyColumn(
                Modifier.fillMaxSize().padding(end = 12.dp),
                state = state,
            ) {
                items(10_000) { index -> SampleItem(index, Modifier.height(64.dp).fillMaxWidth()) }
            }
            VerticalScrollbar(state, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Preview
@Composable
private fun VerticalLazyListSectionPreview() {
    GalleryTheme {
        Column(Modifier.padding(16.dp)) {
            VerticalLazyListSection()
        }
    }
}
