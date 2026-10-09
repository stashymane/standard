package dev.stashy.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun <Entry : MultiBackStack.Entry<Group>, Group : Any> SyncBrowserHistory(
    backStack: MultiBackStack<Entry, Group>,
    pathOf: (Entry) -> String,
    parsePath: (String) -> Entry,
) = Unit
