package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.stashy.navigation.BrowserHistorySession
import dev.stashy.navigation.NavDestination
import dev.stashy.navigation.NavigationState

@Composable
public actual fun SyncBrowserHistory(
    state: NavigationState,
    pathOf: (NavDestination) -> String,
    parsePath: (String) -> NavDestination,
    applyEntry: (NavDestination) -> Unit,
) {
    val pathOfState by rememberUpdatedState(pathOf)
    val parsePathState by rememberUpdatedState(parsePath)
    val applyEntryState by rememberUpdatedState(applyEntry)
    val backStack = state.entries

    val session =
        remember(backStack) {
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
