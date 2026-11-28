package dev.stashy.standard.samples.gallery.screens.relativetime

import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.stashy.standard.samples.gallery.GalleryScreen
import dev.stashy.standard.samples.gallery.components.SampleContent
import dev.stashy.standard.samples.gallery.screens.relativetime.sections.RelativeTimeSection
import dev.stashy.standard.samples.gallery.theme.GalleryTheme
import org.koin.core.annotation.Factory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Factory(binds = [GalleryScreen::class])
internal class RelativeTimeStringScreen : GalleryScreen {
    override val id = "relative-time"
    override val title = "Relative time"
    override val description = "Localized relative time strings that update automatically."

    @Composable
    override fun Content() {
        val now = Clock.System.now()

        @OptIn(ExperimentalGridApi::class)
        SampleContent {
            RelativeTimeSection(
                title = "Past",
                instants = listOf(
                    now.minus(30.seconds),
                    now.minus(5.minutes),
                    now.minus(3.hours),
                    now.minus(4.days),
                ),
            )
            RelativeTimeSection(
                title = "Future",
                instants = listOf(
                    now.plus(30.seconds),
                    now.plus(5.minutes),
                    now.plus(3.hours),
                    now.plus(4.days),
                ),
            )
            RelativeTimeSection(
                title = "Single component",
                instants = listOf(now.minus(4.days).minus(3.hours)),
                maxComponents = 1,
            )
        }
    }
}

@Preview
@Composable
private fun RelativeTimeStringScreenPreview() {
    GalleryTheme {
        RelativeTimeStringScreen().Content()
    }
}
