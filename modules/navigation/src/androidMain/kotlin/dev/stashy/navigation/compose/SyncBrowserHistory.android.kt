package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey

@Composable
public actual fun <Entry : NavKey> SyncBrowserHistory(
    backStack: MutableList<Entry>,
    pathOf: (Entry) -> String,
    parsePath: (String) -> Entry,
    applyEntry: (Entry) -> Unit,
): Unit = Unit
