package dev.stashy.standard.samples.gallery.screens.scrollbar.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.stashy.standard.samples.gallery.theme.GalleryTheme

@Composable
internal fun SampleCell(index: Int) {
    Column(
        Modifier
            .width(48.dp)
            .fillMaxHeight()
            .padding(2.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Text("$index", Modifier.padding(4.dp))
    }
}

@Preview
@Composable
private fun SampleCellPreview() {
    GalleryTheme {
        Row(Modifier.padding(16.dp).height(100.dp)) {
            SampleCell(0)
            SampleCell(1)
        }
    }
}
