package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.stashy.navigation.NavDestination
import dev.stashy.navigation.NavigationState

/**
 * Remembers a [NavigationState] for [startRoute].
 *
 * Group stacks beyond the start group are created lazily by [dev.stashy.navigation.Navigator].
 */
@Composable
public fun rememberNavigationState(
    startRoute: NavDestination,
): NavigationState = remember(startRoute) { NavigationState(startRoute) }
