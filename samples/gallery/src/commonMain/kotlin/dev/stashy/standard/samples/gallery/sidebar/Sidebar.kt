package dev.stashy.standard.samples.gallery.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.stashy.standard.samples.gallery.GalleryScreen
import dev.stashy.standard.samples.gallery.icons.Icons
import dev.stashy.standard.samples.gallery.icons.outlined.ArrowBack24Dp
import dev.stashy.standard.samples.gallery.theme.GalleryTheme

@Composable
internal fun Sidebar(
    screens: List<GalleryScreen>,
    selectedScreenId: String?,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSelect: (GalleryScreen) -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(onClick = onBack, enabled = canGoBack) {
                    Icon(Icons.Outlined.ArrowBack24Dp, null)
                }

                Text(
                    "Component Gallery",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.weight(1f),
            ) {
                items(screens, key = { it.id }) { screen ->
                    val selected = screen.id == selectedScreenId

                    ListItem(
                        modifier = Modifier.clickable { onSelect(screen) },
                        headlineContent = { Text(screen.title) },
                        colors = if (selected) ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            headlineColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            supportingColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        ) else ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = MaterialTheme.colorScheme.onSurface,
                            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            HorizontalDivider()

            Row(
                Modifier.fillMaxWidth()
                    .clickable { onDarkThemeChange(!darkTheme) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Dark theme", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
            }
        }
    }
}

private class PreviewScreen(
    override val id: String,
    override val title: String,
    override val description: String,
) : GalleryScreen {
    @Composable
    override fun Content() {
    }
}

private val PreviewScreens = listOf(
    PreviewScreen("inline-icon", "Inline icon", "Icon scaled to the current text style."),
    PreviewScreen("relative-time", "Relative time", "Localized relative time strings."),
    PreviewScreen("vertical-scrollbar", "Vertical scrollbar", "Vertical scrollbar samples."),
)

@Preview
@Composable
private fun SidebarPreview() {
    GalleryTheme {
        Sidebar(
            screens = PreviewScreens,
            selectedScreenId = "vertical-scrollbar",
            canGoBack = true,
            onBack = {},
            onSelect = {},
            darkTheme = true,
            onDarkThemeChange = {},
            modifier = Modifier.width(280.dp).fillMaxHeight(),
        )
    }
}
