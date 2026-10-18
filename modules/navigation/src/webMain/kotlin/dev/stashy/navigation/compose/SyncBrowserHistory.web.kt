package dev.stashy.navigation.compose

import androidx.compose.runtime.*
import dev.stashy.navigation.BrowserHistorySession
import dev.stashy.navigation.NavDestination
import dev.stashy.navigation.NavigationState
import dev.stashy.navigation.hydrateEntryStatesFromHistory
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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

    val session = remember(state.entryStates) {
        hydrateEntryStatesFromHistory(state.entryStates)
        BrowserHistorySession(
            backStack = backStack,
            entryStateStore = state.entryStates,
            pathOf = pathOfState,
            parsePath = parsePathState,
            applyEntry = applyEntryState,
        )
    }

    LaunchedEffect(session) {
        coroutineScope {
            launch { session.syncFromBackStack() }
            launch { session.bind() }
        }
    }

    DisposableEffect(session) {
        session.attachPopStateListener()
        onDispose { session.detachPopStateListener() }
    }
}
