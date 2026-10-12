package dev.stashy.navigation

import androidx.compose.runtime.Composable
import io.ktor.resources.Resource
import io.ktor.resources.serialization.ResourcesFormat
import kotlinx.serialization.serializer

/**
 * Keeps [backStack] in sync with the browser History API on web targets.
 * No-op on non-browser platforms.
 *
 * @param pathOf maps a stack entry to its URL location (Ktor [io.ktor.http.Url.fullPath], path + query)
 * @param parsePath maps a URL location to a stack entry
 */
@Composable
expect fun <Entry : GroupNavKey<Group>, Group : Any> SyncBrowserHistory(
    backStack: GroupBackStack<Entry, Group>,
    pathOf: (Entry) -> String,
    parsePath: (String) -> Entry,
)

/**
 * Keeps [backStack] in sync with the browser History API using [@Resource][Resource] routes.
 *
 * [Entry] must be a `@Serializable` sealed type whose navigable variants are `@Resource`-annotated
 * (same model as [Ktor type-safe routing](https://ktor.io/docs/server-resources.html)).
 *
 * Unrecognized browser locations fall back to [GroupBackStack.current], then the URL is corrected
 * to match that entry.
 */
@Composable
inline fun <reified Entry : GroupNavKey<Group>, Group : Any> SyncBrowserHistory(
    backStack: GroupBackStack<Entry, Group>,
    resourcesFormat: ResourcesFormat = defaultResourcesFormat,
) {
    SyncBrowserHistory(
        backStack = backStack,
        pathOf = { it.toResourcePath(resourcesFormat) },
        parsePath = { location ->
            fromResourcePath(location, serializer<Entry>(), resourcesFormat)
                ?: backStack.current
        },
    )
}
