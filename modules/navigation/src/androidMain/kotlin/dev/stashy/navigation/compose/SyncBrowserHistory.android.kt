package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.NavigationState

@Composable
public actual fun SyncBrowserHistory(
    state: NavigationState,
    pathOf: (NavKey) -> String,
    parsePath: (String) -> NavKey,
    applyEntry: (NavKey) -> Unit,
): Unit = Unit
