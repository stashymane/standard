package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.InternalNavApi
import dev.stashy.navigation.resources.defaultResourcesFormat
import dev.stashy.navigation.resources.fromResourcePath
import dev.stashy.navigation.resources.toResourcePath
import dev.stashy.navigation.syncTo
import io.ktor.resources.*
import io.ktor.resources.serialization.*
import kotlinx.serialization.serializer
import kotlin.jvm.JvmName

/**
 * Keeps [backStack] in sync with the browser History API on web targets.
 * No-op on non-browser platforms.
 *
 * @param backStack read-only navigation history. Must be a stable, snapshot-backed list (for example
 * [dev.stashy.navigation.GroupBackStack.flattened] or a [androidx.compose.runtime.snapshots.SnapshotStateList]),
 * not an ephemeral copy such as `someStack.toList()`.
 * @param pathOf maps a stack entry to its URL location (Ktor [io.ktor.http.Url.fullPath], path + query)
 * @param parsePath maps a URL location to a stack entry
 * @param applyEntry applies [parsePath] results to the navigation stack
 */
@Composable
public expect fun <Entry : NavKey> SyncBrowserHistory(
    backStack: List<Entry>,
    pathOf: (Entry) -> String,
    parsePath: (String) -> Entry,
    applyEntry: (Entry) -> Unit,
)

/**
 * Keeps a [MutableList] back stack in sync with the browser History API.
 */
@OptIn(InternalNavApi::class)
@Composable
public fun <Entry : NavKey> SyncBrowserHistory(
    backStack: MutableList<Entry>,
    pathOf: (Entry) -> String,
    parsePath: (String) -> Entry,
    applyEntry: (Entry) -> Unit = { entry -> backStack.syncTo(entry) },
) {
    SyncBrowserHistory(
        backStack = backStack,
        pathOf = pathOf,
        parsePath = parsePath,
        applyEntry = applyEntry,
    )
}

/**
 * Keeps [backStack] in sync with the browser History API using [@Resource][Resource] routes.
 *
 * [Entry] must be a `@Serializable` sealed type whose navigable variants are `@Resource`-annotated
 * (same model as [Ktor type-safe routing](https://ktor.io/docs/server-resources.html)).
 *
 * Unrecognized browser locations fall back to the current top of [backStack], then the URL is
 * corrected to match that entry.
 */
@Composable
public inline fun <reified Entry : NavKey> SyncBrowserHistory(
    backStack: List<Entry>,
    resourcesFormat: ResourcesFormat = defaultResourcesFormat,
    noinline applyEntry: (Entry) -> Unit,
) {
    SyncBrowserHistory(
        backStack = backStack,
        pathOf = { it.toResourcePath(serializer<Entry>(), resourcesFormat) },
        parsePath = { location ->
            fromResourcePath(location, serializer<Entry>(), resourcesFormat)
                ?: backStack.last()
        },
        applyEntry = applyEntry,
    )
}

/**
 * Resource-based browser sync for a [MutableList] back stack.
 */
@OptIn(InternalNavApi::class)
@Composable
@JvmName("SyncBrowserHistoryMutableList")
public inline fun <reified Entry : NavKey> SyncBrowserHistory(
    backStack: MutableList<Entry>,
    resourcesFormat: ResourcesFormat = defaultResourcesFormat,
    noinline applyEntry: (Entry) -> Unit = { entry -> backStack.syncTo(entry) },
) {
    val history: List<Entry> = backStack
    SyncBrowserHistory(
        backStack = history,
        resourcesFormat = resourcesFormat,
        applyEntry = applyEntry,
    )
}
