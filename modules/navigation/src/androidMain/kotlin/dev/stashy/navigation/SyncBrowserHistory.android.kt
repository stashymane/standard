package dev.stashy.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun <Entry : GroupNavKey<Group>, Group : Any> SyncBrowserHistory(
    backStack: GroupBackStack<Entry, Group>,
    pathOf: (Entry) -> String,
    parsePath: (String) -> Entry,
) = Unit
