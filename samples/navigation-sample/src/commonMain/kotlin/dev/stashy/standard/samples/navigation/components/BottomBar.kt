package dev.stashy.standard.samples.navigation.components

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import dev.stashy.standard.samples.navigation.Route

@Composable
internal fun BottomBar(
    current: NavKey,
    onSelect: (NavKey) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = current == Route.Home,
            onClick = { onSelect(Route.Home) },
            icon = { Text("H") },
            label = { Text("Home") },
        )
        NavigationBarItem(
            selected = current == Route.Search,
            onClick = { onSelect(Route.Search) },
            icon = { Text("S") },
            label = { Text("Search") },
        )
        NavigationBarItem(
            selected = current is Route.Profile,
            onClick = { onSelect(Route.Profile("me")) },
            icon = { Text("P") },
            label = { Text("Profile") },
        )
    }
}
