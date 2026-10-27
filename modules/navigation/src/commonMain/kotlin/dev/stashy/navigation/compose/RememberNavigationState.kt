package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import dev.stashy.navigation.state.NavigationState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Remembers a [NavigationState] for [startRoute] and [topLevelRoutes], persisting the current
 * top-level route and each back stack using [serializer].
 *
 * @param startRoute the route the user starts at; must be in [topLevelRoutes]
 * @param topLevelRoutes the top-level routes that can be switched between
 * @param serializer serializes every route stored in the navigation state
 */
@Composable
public fun rememberNavigationState(
    startRoute: NavKey,
    topLevelRoutes: Set<NavKey>,
    serializer: KSerializer<out NavKey>,
): NavigationState {
    val navKeySerializer = serializer.asNavKeySerializer()
    val topLevelRoute = rememberSerializable(
        startRoute,
        topLevelRoutes,
        navKeySerializer,
        stateSerializer = navKeySerializer,
    ) {
        mutableStateOf(startRoute)
    }

    val backStacks = topLevelRoutes.associateWith { route ->
        key(route) {
            rememberSerializable(
                navKeySerializer,
                serializer = NavBackStackSerializer(navKeySerializer),
            ) {
                NavBackStack(route)
            }
        }
    }

    return remember(startRoute, topLevelRoutes, navKeySerializer) {
        NavigationState(
            startRoute = startRoute,
            topLevelRoute = topLevelRoute,
            backStacks = backStacks,
        )
    }
}

/**
 * Remembers a [NavigationState] using the automatically generated serializer for [Route].
 *
 * @param startRoute the route the user starts at; must be in [topLevelRoutes]
 * @param topLevelRoutes the top-level routes that can be switched between
 */
@Composable
public inline fun <reified Route : NavKey> rememberNavigationState(
    startRoute: Route,
    topLevelRoutes: Set<Route>,
): NavigationState = rememberNavigationState(
    startRoute = startRoute,
    topLevelRoutes = topLevelRoutes,
    serializer = serializer<Route>(),
)

@Suppress("UNCHECKED_CAST")
private fun KSerializer<out NavKey>.asNavKeySerializer(): KSerializer<NavKey> =
    this as KSerializer<NavKey>
