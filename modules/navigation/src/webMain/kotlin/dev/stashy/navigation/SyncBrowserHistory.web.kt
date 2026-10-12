package dev.stashy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

@Composable
actual fun <Entry : GroupNavKey<Group>, Group : Any> SyncBrowserHistory(
    backStack: GroupBackStack<Entry, Group>,
    pathOf: (Entry) -> String,
    parsePath: (String) -> Entry,
) {
    val session = remember(backStack, pathOf, parsePath) {
        BrowserHistorySession(backStack, pathOf, parsePath)
    }

    LaunchedEffect(session) {
        session.bind()
    }

    DisposableEffect(session) {
        session.attachPopStateListener()
        onDispose { session.detachPopStateListener() }
    }
}
