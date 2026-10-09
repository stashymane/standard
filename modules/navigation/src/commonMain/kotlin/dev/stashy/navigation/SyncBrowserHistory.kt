package dev.stashy.navigation

import androidx.compose.runtime.Composable

/**
 * Keeps [backStack] in sync with the browser History API on web targets.
 * No-op on non-browser platforms.
 *
 * @param pathOf maps a stack entry to its URL path
 * @param parsePath maps a URL path to a stack entry (must not return null for handled routes)
 */
@Composable
expect fun <Entry : MultiBackStack.Entry<Group>, Group : Any> SyncBrowserHistory(
    backStack: MultiBackStack<Entry, Group>,
    pathOf: (Entry) -> String,
    parsePath: (String) -> Entry,
)
