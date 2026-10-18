package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import dev.stashy.navigation.EntryStateStore
import dev.stashy.navigation.NavDestination
import dev.stashy.navigation.NavigationState

/**
 * Provides [LocalEntryState] for each rendered [NavEntry] and clears stored state when the entry
 * is popped from the back stack.
 */
@Composable
public fun rememberEntryStateNavEntryDecorator(
    state: NavigationState,
): EntryStateNavEntryDecorator = remember(state.entryStates) { EntryStateNavEntryDecorator(state.entryStates) }

/**
 * @see rememberEntryStateNavEntryDecorator
 */
public class EntryStateNavEntryDecorator internal constructor(
    private val store: EntryStateStore,
) : NavEntryDecorator<NavDestination>(
    onPop = { contentKey -> store.remove(contentKey.toString()) },
    decorate = { entry -> EntryStateNavEntryContent(store, entry) },
)

@Composable
private fun EntryStateNavEntryContent(
    store: EntryStateStore,
    entry: NavEntry<NavDestination>,
) {
    val entryId = entry.contentKey.toString()
    CompositionLocalProvider(LocalEntryState provides EntryStateScope(store, entryId)) {
        entry.Content()
    }
}
