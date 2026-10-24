package dev.stashy.navigation.compose

import androidx.compose.runtime.*
import dev.stashy.navigation.NavDefaults
import dev.stashy.navigation.state.EntryStateScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * [EntryStateScope] for the current nav entry, or an error if the entry-state decorator is missing.
 */
public val LocalEntryState: ProvidableCompositionLocal<EntryStateScope> = staticCompositionLocalOf {
    error("No LocalEntryState provided. Add rememberEntryStateNavEntryDecorator to NavDisplay entryDecorators.")
}

/**
 * Remembers serializable state scoped to the current nav entry.
 *
 * State survives while the entry remains on the back stack and, on web, is persisted in
 * `history.state` (not the URL) by the browser-history navigator.
 *
 * @param key logical key for this value; defaults to a hash of the call site
 */
@Composable
public fun <T> rememberNavEntryState(
    serializer: KSerializer<T>,
    key: String? = null,
    init: () -> T,
): MutableState<T> {
    val scope = LocalEntryState.current
    val resolvedKey = key ?: currentCompositeKeyHashCode.toString(EntryStateScope.MAX_RADIX)

    val initial = remember(scope.entryId, resolvedKey) {
        scope.store.getString(scope.entryId, resolvedKey)?.let { stored ->
            NavDefaults.json.decodeFromString(serializer, stored)
        } ?: init()
    }
    val state = remember(scope.entryId, resolvedKey) { mutableStateOf(initial) }

    LaunchedEffect(scope.entryId, resolvedKey) {
        snapshotFlow(state::value).collect { value ->
            val json = NavDefaults.json.encodeToString(serializer, value)
            scope.store.setString(scope.entryId, resolvedKey, json)
        }
    }

    return state
}

/**
 * @see rememberNavEntryState
 */
@Composable
public inline fun <reified T> rememberNavEntryState(
    key: String? = null,
    noinline init: () -> T,
): MutableState<T> = rememberNavEntryState(serializer(), key, init)
