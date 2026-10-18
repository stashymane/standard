package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.NavEntryProviderScope
import dev.stashy.navigation.NavGraph
import dev.stashy.navigation.NavigationState
import dev.stashy.navigation.navGraph

@Composable
public fun rememberNavigationState(
    startRoute: NavKey,
    graph: NavGraph<*>,
): NavigationState = remember(startRoute, graph) {
    NavigationState(startRoute, graph)
}

@Composable
public fun <T : Any> rememberNavigationState(
    startRoute: NavKey,
    builder: NavEntryProviderScope<T>.() -> Unit,
): NavigationState = remember(startRoute, builder) {
    NavigationState(startRoute, navGraph(builder = builder))
}
