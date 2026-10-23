package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.NavigationState

/**
 * Remembers a [NavigationState] for [startRoute] and the given [topLevelRoutes].
 *
 * @param startRoute the route the user starts at; must be in [topLevelRoutes]
 * @param topLevelRoutes the top-level routes that can be switched between
 */
@Composable
public fun rememberNavigationState(
    startRoute: NavKey,
    topLevelRoutes: Set<NavKey>,
): NavigationState = remember(startRoute, topLevelRoutes) {
    NavigationState(startRoute, topLevelRoutes)
}
