package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.BrowserHistoryNavigator
import dev.stashy.navigation.Navigator
import dev.stashy.navigation.state.NavigationState

@Composable
public actual fun rememberNavigator(
    state: NavigationState,
    pathOf: (NavKey) -> String,
    parsePath: (String) -> NavKey,
): Navigator {
    val navigator = remember(state, pathOf, parsePath) {
        BrowserHistoryNavigator(state, pathOf, parsePath)
    }

    DisposableEffect(navigator) {
        navigator.attach()
        onDispose { navigator.detach() }
    }

    LaunchedEffect(navigator) {
        navigator.syncEntryStates()
    }

    return navigator
}
