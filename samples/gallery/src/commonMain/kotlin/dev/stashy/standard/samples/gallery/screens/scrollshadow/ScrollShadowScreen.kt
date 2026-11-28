package dev.stashy.standard.samples.gallery.screens.scrollshadow

import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.stashy.standard.samples.gallery.GalleryScreen
import dev.stashy.standard.samples.gallery.components.SampleContent
import dev.stashy.standard.samples.gallery.screens.scrollshadow.sections.LazyListScrollShadowSection
import dev.stashy.standard.samples.gallery.screens.scrollshadow.sections.ScrollStateScrollShadowSection
import dev.stashy.standard.samples.gallery.theme.GalleryTheme
import org.koin.core.annotation.Factory

@Factory(binds = [GalleryScreen::class])
internal class ScrollShadowScreen : GalleryScreen {
    override val id = "scroll-shadow"
    override val title = "Scroll shadow"
    override val description = "Edge shadows that fade out as the scroll position reaches the ends."

    @Composable
    override fun Content() {
        @OptIn(ExperimentalGridApi::class)
        SampleContent {
            ScrollStateScrollShadowSection()
            LazyListScrollShadowSection()
        }
    }
}

@Preview
@Composable
private fun ScrollShadowScreenPreview() {
    GalleryTheme {
        ScrollShadowScreen().Content()
    }
}
