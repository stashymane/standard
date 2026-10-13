package dev.stashy.navigation.compose

import androidx.compose.runtime.*
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.BrowserHistorySession

@Composable
public actual fun <Entry : NavKey> SyncBrowserHistory(
    backStack: List<Entry>,
    pathOf: (Entry) -> String,
    parsePath: (String) -> Entry,
    applyEntry: (Entry) -> Unit,
) {
    val pathOfState by rememberUpdatedState(pathOf)
    val parsePathState by rememberUpdatedState(parsePath)
    val applyEntryState by rememberUpdatedState(applyEntry)

    val session = remember(backStack) {
        BrowserHistorySession(
            backStack = backStack,
            pathOf = pathOfState,
            parsePath = parsePathState,
            applyEntry = applyEntryState,
        )
    }

    LaunchedEffect(session) {
        session.bind()
    }

    DisposableEffect(session) {
        session.attachPopStateListener()
        onDispose { session.detachPopStateListener() }
    }
}
