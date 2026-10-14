package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import dev.stashy.navigation.NavDestination
import dev.stashy.navigation.NavigationState
import dev.stashy.navigation.Navigator
import dev.stashy.navigation.resources.defaultResourcesFormat
import dev.stashy.navigation.resources.fromResourcePath
import dev.stashy.navigation.resources.toResourcePath
import io.ktor.resources.Resource
import io.ktor.resources.serialization.ResourcesFormat
import kotlinx.serialization.serializer

/**
 * Keeps [NavigationState.entries] in sync with the browser History API on web targets.
 * No-op on non-browser platforms.
 *
 * @param state navigation state whose [NavigationState.entries] are observed
 * @param pathOf maps a destination to its URL location (Ktor [io.ktor.http.Url.fullPath], path + query)
 * @param parsePath maps a URL location to a destination
 * @param applyEntry applies [parsePath] results (typically [Navigator.popToOrNavigate])
 */
@Composable
public expect fun SyncBrowserHistory(
    state: NavigationState,
    pathOf: (NavDestination) -> String,
    parsePath: (String) -> NavDestination,
    applyEntry: (NavDestination) -> Unit,
)

/**
 * Keeps [navigator]'s [NavigationState.entries] in sync with the browser History API,
 * applying URL changes via [Navigator.popToOrNavigate].
 */
@Composable
public fun SyncBrowserHistory(
    navigator: Navigator,
    pathOf: (NavDestination) -> String,
    parsePath: (String) -> NavDestination,
) {
    SyncBrowserHistory(
        state = navigator.state,
        pathOf = pathOf,
        parsePath = parsePath,
        applyEntry = navigator::popToOrNavigate,
    )
}

/**
 * Keeps [state] in sync with the browser History API using [@Resource][Resource] routes.
 *
 * [Entry] must be a `@Serializable` sealed type whose navigable variants are `@Resource`-annotated
 * (same model as [Ktor type-safe routing](https://ktor.io/docs/server-resources.html)).
 *
 * Unrecognized browser locations fall back to the current top of [NavigationState.entries], then
 * the URL is corrected to match that entry.
 */
@Composable
public inline fun <reified Entry : NavDestination> SyncBrowserHistory(
    state: NavigationState,
    resourcesFormat: ResourcesFormat = defaultResourcesFormat,
    noinline applyEntry: (Entry) -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    val history = state.entries as List<Entry>
    SyncBrowserHistory(
        state = state,
        pathOf = { destination ->
            (destination as Entry).toResourcePath(serializer<Entry>(), resourcesFormat)
        },
        parsePath = { location ->
            fromResourcePath(location, serializer<Entry>(), resourcesFormat)
                ?: history.last()
        },
        applyEntry = { destination -> applyEntry(destination as Entry) },
    )
}

/**
 * Resource-based browser sync for a [Navigator].
 */
@Composable
public inline fun <reified Entry : NavDestination> SyncBrowserHistory(
    navigator: Navigator,
    resourcesFormat: ResourcesFormat = defaultResourcesFormat,
) {
    SyncBrowserHistory<Entry>(
        state = navigator.state,
        resourcesFormat = resourcesFormat,
        applyEntry = { entry -> navigator.popToOrNavigate(entry) },
    )
}
