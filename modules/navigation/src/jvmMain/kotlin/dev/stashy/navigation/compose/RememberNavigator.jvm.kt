package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.DefaultNavigator
import dev.stashy.navigation.Navigator
import dev.stashy.navigation.state.NavigationState

@Composable
public actual fun rememberNavigator(
    state: NavigationState,
    pathOf: (NavKey) -> String,
    parsePath: (String) -> NavKey,
): Navigator = remember(state) { DefaultNavigator(state) }
