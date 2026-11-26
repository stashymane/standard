package dev.stashy.standard.samples.gallery.screens.scrollshadow.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.stashy.standard.ui.modifiers.verticalScrollShadow

@Composable
internal fun LazyListScrollShadowSection() {
    Section("LazyListState") {
        val state = rememberLazyListState()

        Box(Modifier.fillMaxWidth().height(200.dp)) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .verticalScrollShadow(state)
                    .padding(horizontal = 12.dp)
                    .padding(end = 8.dp),
                state = state,
            ) {
                items(40) { index -> SampleItem(index) }
            }
            VerticalScrollbar(state, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Preview
@Composable
private fun LazyListScrollShadowSectionPreview() {
    GalleryTheme {
        Column(Modifier.padding(16.dp)) {
            LazyListScrollShadowSection()
        }
    }
}
