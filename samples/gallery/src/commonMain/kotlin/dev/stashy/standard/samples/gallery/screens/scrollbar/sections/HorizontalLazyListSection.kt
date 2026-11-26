package dev.stashy.standard.samples.gallery.screens.scrollbar.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.stashy.standard.samples.gallery.components.Section
import dev.stashy.standard.samples.gallery.theme.GalleryTheme
import dev.stashy.standard.ui.components.scroll.HorizontalScrollbar

@Composable
internal fun HorizontalLazyListSection() {
    Section("Horizontal · LazyListState") {
        val state = rememberLazyListState()

        Box(Modifier.heightIn(max = 100.dp).align(Alignment.CenterHorizontally)) {
            LazyRow(
                Modifier.fillMaxSize().padding(bottom = 8.dp),
                state = state,
            ) {
                items(1_000) { index -> SampleCell(index) }
            }
            HorizontalScrollbar(state, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Preview
@Composable
private fun HorizontalLazyListSectionPreview() {
    GalleryTheme {
        Column(Modifier.padding(16.dp)) {
            HorizontalLazyListSection()
        }
    }
}
