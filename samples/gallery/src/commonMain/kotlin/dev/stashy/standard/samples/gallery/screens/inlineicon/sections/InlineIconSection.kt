package dev.stashy.standard.samples.gallery.screens.inlineicon.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.stashy.standard.samples.gallery.components.Section
import dev.stashy.standard.samples.gallery.icons.Icons
import dev.stashy.standard.samples.gallery.icons.outlined.Favorite24Dp
import dev.stashy.standard.samples.gallery.icons.outlined.Star24Dp
import dev.stashy.standard.samples.gallery.theme.GalleryTheme
import dev.stashy.standard.ui.components.InlineIcon

@Composable
internal fun InlineIconSection(
    style: TextStyle = MaterialTheme.typography.bodyMedium
) = Section("size=${style.fontSize}, lineHeight=${style.lineHeight}, weight=${style.fontWeight?.weight}") {
    ProvideTextStyle(style) {
        val gap = with(LocalDensity.current) { style.lineHeight.toDp() / 2 }

        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Favourited")
            InlineIcon(Icons.Outlined.Favorite24Dp, contentDescription = "Favourite")
            InlineIcon(Icons.Outlined.Star24Dp, contentDescription = "Star")
        }
    }
}

@Preview
@Composable
private fun InlineIconSectionPreview() {
    GalleryTheme {
        Column(Modifier.padding(16.dp)) {
            InlineIconSection()
            InlineIconSection(MaterialTheme.typography.titleSmall)
            InlineIconSection(MaterialTheme.typography.titleMedium)
            InlineIconSection(MaterialTheme.typography.titleLarge)
        }
    }
}
