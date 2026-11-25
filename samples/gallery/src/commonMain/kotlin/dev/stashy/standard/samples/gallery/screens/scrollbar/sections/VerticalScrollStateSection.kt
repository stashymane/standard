package dev.stashy.standard.samples.gallery.screens.scrollbar.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
internal fun VerticalScrollStateSection() {
    Section("Vertical · ScrollState") {
        val state = rememberScrollState()

        Box(Modifier.size(260.dp, 200.dp).align(Alignment.CenterHorizontally)) {
            Column(
                Modifier.fillMaxSize().padding(end = 8.dp).verticalScroll(state),
            ) {
                repeat(1000) { index -> SampleItem(index) }
            }
            VerticalScrollbar(state, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Preview
@Composable
private fun VerticalScrollStateSectionPreview() {
    GalleryTheme {
        Column(Modifier.padding(16.dp)) {
            VerticalScrollStateSection()
        }
    }
}
