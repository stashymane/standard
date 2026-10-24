package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.state.EntryStateScope
import dev.stashy.navigation.state.EntryStateStore
import dev.stashy.navigation.state.NavigationState

/**
 * @see rememberEntryStateNavEntryDecorator
 */
public class EntryStateNavEntryDecorator internal constructor(
    private val store: EntryStateStore,
) : NavEntryDecorator<NavKey>(
    onPop = { contentKey -> store.remove(contentKey.toString()) },
    decorate = { entry -> EntryStateNavEntryContent(store, entry) },
)

@Composable
private fun EntryStateNavEntryContent(
    store: EntryStateStore,
    entry: NavEntry<NavKey>,
) {
    val entryId = entry.contentKey.toString()
    CompositionLocalProvider(LocalEntryState provides EntryStateScope(store, entryId)) {
        entry.Content()
    }
}

/**
 * Provides [dev.stashy.navigation.state.LocalEntryState] for each rendered [NavEntry] and clears stored state when the entry
 * is popped from the back stack.
 */
@Composable
public fun rememberEntryStateNavEntryDecorator(
    state: NavigationState,
): EntryStateNavEntryDecorator = remember(state.entryStates) { EntryStateNavEntryDecorator(state.entryStates) }
