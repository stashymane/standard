package dev.stashy.standard.samples.gallery.screens.scrollbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.stashy.standard.samples.gallery.GalleryScreen
import dev.stashy.standard.samples.gallery.components.SampleContent
import dev.stashy.standard.samples.gallery.screens.scrollbar.sections.HorizontalLazyListSection
import dev.stashy.standard.samples.gallery.screens.scrollbar.sections.HorizontalScrollStateSection
import dev.stashy.standard.samples.gallery.screens.scrollbar.sections.VariableHeightLazyListSection
import dev.stashy.standard.samples.gallery.screens.scrollbar.sections.VerticalLazyListSection
import dev.stashy.standard.samples.gallery.screens.scrollbar.sections.VerticalScrollStateSection
import dev.stashy.standard.samples.gallery.theme.GalleryTheme
import org.koin.core.annotation.Factory

@Factory(binds = [GalleryScreen::class])
internal class ScrollbarScreen : GalleryScreen {
    override val id = "scrollbar"
    override val title = "Scrollbar"
    override val description = "Vertical and horizontal scrollbars bound to ScrollState and LazyListState."

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
