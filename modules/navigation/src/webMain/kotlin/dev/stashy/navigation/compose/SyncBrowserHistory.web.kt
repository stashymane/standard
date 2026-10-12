package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.BrowserHistorySession

@Composable
public actual fun <Entry : NavKey> SyncBrowserHistory(
    backStack: MutableList<Entry>,
    pathOf: (Entry) -> String,
    parsePath: (String) -> Entry,
    applyEntry: (Entry) -> Unit,
) {
    val session = remember(backStack, pathOf, parsePath, applyEntry) {
        BrowserHistorySession(backStack, pathOf, parsePath, applyEntry)
    }

    LaunchedEffect(session) {
        session.bind()
    }

    DisposableEffect(session) {
        session.attachPopStateListener()
        onDispose { session.detachPopStateListener() }
    }
}
