package dev.stashy.standard.samples.gallery.screens.relativetime.sections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.stashy.standard.samples.gallery.components.Section
import dev.stashy.standard.samples.gallery.theme.GalleryTheme
import dev.stashy.standard.ui.components.toRelativeString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Composable
internal fun RelativeTimeSection(
    title: String,
    instants: List<Instant>,
    maxComponents: Int = 2,
) = Section(title) {
    instants.forEach { instant ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(instant.toRelativeString(maxComponents = maxComponents))

            Spacer(Modifier.weight(1f))

            Text(instant.toString(), color = LocalContentColor.current.copy(alpha = 0.75f))
        }
    }
}

@Preview
@Composable
private fun RelativeTimeSectionPreview() {
    val now = Clock.System.now()

    GalleryTheme {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RelativeTimeSection(
                title = "Past",
                instants = listOf(now.minus(5.minutes), now.minus(3.hours)),
            )
            RelativeTimeSection(
                title = "Single component",
                instants = listOf(now.minus(3.hours)),
                maxComponents = 1,
            )
        }
    }
}
