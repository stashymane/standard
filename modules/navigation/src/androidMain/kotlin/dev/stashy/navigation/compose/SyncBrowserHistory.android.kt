package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import dev.stashy.navigation.NavDestination
import dev.stashy.navigation.NavigationState

@Composable
public actual fun SyncBrowserHistory(
    state: NavigationState,
    pathOf: (NavDestination) -> String,
    parsePath: (String) -> NavDestination,
    applyEntry: (NavDestination) -> Unit,
): Unit = Unit
