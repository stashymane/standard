package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.Navigator
import dev.stashy.navigation.resources.defaultResourcesFormat
import dev.stashy.navigation.resources.fromResourcePath
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
        (destination as Entry).toResourcePath(serializer<Entry>(), resourcesFormat)
    },
    parsePath = { location ->
        fromResourcePath(location, serializer<Entry>(), resourcesFormat)
            ?: state.backStack.last()
    },
)
