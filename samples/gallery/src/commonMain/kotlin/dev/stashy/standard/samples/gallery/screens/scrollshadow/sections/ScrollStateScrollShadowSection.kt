package dev.stashy.standard.samples.gallery.screens.scrollshadow.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import dev.stashy.standard.ui.modifiers.verticalScrollShadow

@Composable
internal fun ScrollStateScrollShadowSection() {
    Section("ScrollState") {
        val state = rememberScrollState()

        Box(Modifier.fillMaxWidth().height(200.dp)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScrollShadow(state)
                    .verticalScroll(state)
                    .padding(12.dp)
                    .padding(end = 12.dp),
            ) {
                repeat(20) { index -> SampleItem(index) }
            }
            VerticalScrollbar(state, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Preview
@Composable
private fun ScrollStateScrollShadowSectionPreview() {
    GalleryTheme {
        Column(Modifier.padding(16.dp)) {
            ScrollStateScrollShadowSection()
        }
    }
}
