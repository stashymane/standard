package dev.stashy.navigation

import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.state.NavigationState

internal data object Home : NavKey
internal data object HomeDetail : NavKey
internal data object Search : NavKey
internal data object SearchResult : NavKey
internal data object Profile : NavKey
internal data object Confirm : OverlayNavKey
internal data class Share(val id: Int) : OverlayNavKey

internal val testTopLevelRoutes: Set<NavKey> = setOf(Home, Search, Profile)

internal fun testNavigator(): Navigator =
    DefaultNavigator(NavigationState(Home, testTopLevelRoutes))
