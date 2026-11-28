package dev.stashy.standard.samples.gallery.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.stashy.standard.samples.gallery.theme.GalleryTheme

/** A simple list item used by the scrollable samples. */
@Composable
internal fun SampleItem(index: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(2.dp)
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
private fun SampleItemPreview() {
    GalleryTheme {
        Column(Modifier.width(240.dp).padding(16.dp)) {
            SampleItem(0)
            SampleItem(1)
        }
    }
}
