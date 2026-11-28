package dev.stashy.standard.samples.gallery.screens.scrollbar.sections

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.stashy.standard.samples.gallery.components.SampleItem
import dev.stashy.standard.samples.gallery.components.Section
import dev.stashy.standard.samples.gallery.theme.GalleryTheme
import dev.stashy.standard.ui.components.scroll.HorizontalScrollbar

@Composable
internal fun HorizontalScrollStateSection() {
    Section("Horizontal · ScrollState") {
        val state = rememberScrollState()

        Box(Modifier.heightIn(max = 100.dp).align(Alignment.CenterHorizontally)) {
            Row(
                Modifier.fillMaxSize().padding(bottom = 12.dp).horizontalScroll(state),
            ) {
                repeat(1000) { index -> SampleItem(index, Modifier.width(64.dp).fillMaxHeight()) }
            }
            HorizontalScrollbar(state, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Preview
@Composable
private fun HorizontalScrollStateSectionPreview() {
    GalleryTheme {
        Column(Modifier.padding(16.dp)) {
            HorizontalScrollStateSection()
        }
    }
}
