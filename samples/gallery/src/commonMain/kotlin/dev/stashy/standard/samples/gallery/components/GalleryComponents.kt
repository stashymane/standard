package dev.stashy.standard.samples.gallery.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.stashy.standard.samples.gallery.theme.GalleryTheme
import dev.stashy.standard.ui.components.scroll.VerticalScrollbar

/** Scrollable page layout shared by the sample screens. */
@Composable
internal fun SampleContent(
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content,
        )
        VerticalScrollbar(scrollState, Modifier.align(Alignment.CenterEnd))
    }
}

/** A titled, visually separated block inside a sample screen. */
@Composable
internal fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** A simple list item used by the scrollable samples. */
@Composable
internal fun SampleItem(index: Int, height: Dp = 64.dp) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(2.dp)
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Item $index",
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Preview
@Composable
private fun SampleContentPreview() {
    GalleryTheme {
        SampleContent {
            Section("Section title") {
                SampleItem(0)
                SampleItem(1)
            }
            Section("Another section") {
                SampleItem(2)
            }
        }
    }
}

@Preview
@Composable
private fun SectionPreview() {
    GalleryTheme {
        Column(
            Modifier.width(240.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("Section title") {
                SampleItem(0)
                SampleItem(1)
            }
        }
    }
}

@Preview
@Composable
private fun SampleItemPreview() {
    GalleryTheme {
        Column(Modifier.width(240.dp).padding(16.dp)) {
            SampleItem(0)
            SampleItem(1)
        }
    }
}
