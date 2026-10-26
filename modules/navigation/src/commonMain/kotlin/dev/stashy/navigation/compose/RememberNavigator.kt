package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.Navigator
import dev.stashy.navigation.OverlayNavKey
import dev.stashy.navigation.resources.decodeFromResourcePath
import dev.stashy.navigation.resources.defaultResourcesFormat
import dev.stashy.navigation.resources.toResourcePath
import dev.stashy.navigation.state.NavigationState
import io.ktor.resources.serialization.*
import kotlinx.serialization.serializer

/**
 * Remembers a platform-specific [Navigator] for [state] using explicit path mapping.
 *
 * On web this navigator wraps the browser History API and updates the URL. On other platforms it
 * returns an in-memory navigator; the mapping arguments are ignored.
 */
@Composable
public expect fun rememberNavigator(
    state: NavigationState,
    pathOf: (NavKey) -> String,
    parsePath: (String) -> NavKey,
): Navigator

/**
 * Remembers a platform-specific [Navigator] for [state] using [@Resource][io.ktor.resources.Resource]
 * serialization to map routes to URL paths.
 *
 * On web this navigator wraps the browser History API and updates the URL. On other platforms it
 * returns an in-memory navigator.
 */
@Composable
public inline fun <reified Entry : NavKey> rememberNavigator(
    state: NavigationState,
    resourcesFormat: ResourcesFormat = defaultResourcesFormat,
): Navigator = rememberNavigator(
    state = state,
    pathOf = { destination ->
        check(destination !is OverlayNavKey) {
            "Overlay routes do not have URL paths: $destination"
        }
        (destination as Entry).toResourcePath(serializer<Entry>(), resourcesFormat)
    },
    parsePath = { location ->
        val route =
            decodeFromResourcePath(
                location = location,
                rootSerializer = serializer<Entry>(),
                resourcesFormat = resourcesFormat,
                accept = { candidate -> candidate is Entry && candidate !is OverlayNavKey },
            ) as? Entry
        route ?: state.backStack.lastOrNull { it !is OverlayNavKey } ?: state.startRoute
    },
)
