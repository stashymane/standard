package dev.stashy.standard.samples.gallery.screens.inlineicon

import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.stashy.standard.samples.gallery.GalleryScreen
import dev.stashy.standard.samples.gallery.components.SampleContent
import dev.stashy.standard.samples.gallery.screens.inlineicon.sections.InlineIconSection
import dev.stashy.standard.samples.gallery.theme.GalleryTheme
import org.koin.core.annotation.Factory

@Factory(binds = [GalleryScreen::class])
internal class InlineIconScreen : GalleryScreen {
    override val id = "inline-icon"
    override val title = "Inline icon"
    override val description = "An icon scaled to the current text style's line height."

    @Composable
    override fun Content() {
        @OptIn(ExperimentalGridApi::class)
        SampleContent {
            val styles = listOf(
                MaterialTheme.typography.bodySmall,
                MaterialTheme.typography.bodyMedium,
                MaterialTheme.typography.bodyLarge,
                MaterialTheme.typography.titleSmall,
                MaterialTheme.typography.titleMedium,
                MaterialTheme.typography.titleLarge,
            )
            styles.forEach {
                InlineIconSection(it)
            }
        }
    }
}

@Preview
@Composable
private fun InlineIconScreenPreview() {
    GalleryTheme {
        InlineIconScreen().Content()
    }
}
