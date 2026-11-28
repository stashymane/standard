package dev.stashy.standard.samples.gallery.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.stashy.standard.samples.gallery.theme.GalleryTheme
import dev.stashy.standard.ui.components.scroll.LocalScrollbarStyle
import dev.stashy.standard.ui.components.scroll.VerticalScrollbar
import dev.stashy.standard.ui.responsive.ContainerSize
import dev.stashy.standard.ui.responsive.LocalResponsiveInfo

/** Scrollable page layout shared by the sample screens. */
@OptIn(ExperimentalGridApi::class)
@Composable
internal fun SampleContent(
    content: @Composable GridScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val responsiveInfo = LocalResponsiveInfo.current
    val containerWidth = responsiveInfo.width

    Box(Modifier.fillMaxSize()) {
        Grid(
            {
                gap(8.dp)

                val columns = when {
                    containerWidth <= ContainerSize.Medium -> 1
                    containerWidth <= ContainerSize.Large -> 2
                    else -> 3
                }

                repeat(columns) {
                    column(1f / columns)
                }
            },
            Modifier.fillMaxSize().verticalScroll(scrollState)
                .padding(end = LocalScrollbarStyle.current.thickness * 2)
        ) {
            content()
        }
        VerticalScrollbar(scrollState, Modifier.align(Alignment.CenterEnd))
    }
}


@Preview
@Composable
private fun SampleContentPreview() {
    GalleryTheme {
        @OptIn(ExperimentalGridApi::class)
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
