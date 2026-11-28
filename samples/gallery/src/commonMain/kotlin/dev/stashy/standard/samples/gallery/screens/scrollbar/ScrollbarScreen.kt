package dev.stashy.standard.samples.gallery.screens.scrollbar

import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.stashy.standard.samples.gallery.GalleryScreen
import dev.stashy.standard.samples.gallery.components.SampleContent
import dev.stashy.standard.samples.gallery.screens.scrollbar.sections.*
import dev.stashy.standard.samples.gallery.theme.GalleryTheme
import org.koin.core.annotation.Factory

@Factory(binds = [GalleryScreen::class])
internal class ScrollbarScreen : GalleryScreen {
    override val id = "scrollbar"
    override val title = "Scrollbar"
    override val description = "Vertical and horizontal scrollbars bound to ScrollState and LazyListState."

    @OptIn(ExperimentalGridApi::class)
    @Composable
    override fun Content() {
        SampleContent {
            VerticalScrollStateSection()
            VerticalLazyListSection()
            VariableHeightLazyListSection()
            HorizontalScrollStateSection()
            HorizontalLazyListSection()
        }
    }
}

@Preview
@Composable
private fun ScrollbarScreenPreview() {
    GalleryTheme {
        ScrollbarScreen().Content()
    }
}
