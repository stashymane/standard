package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import dev.stashy.navigation.GroupId
import dev.stashy.navigation.NavDestination
import dev.stashy.navigation.NavigationState
import dev.stashy.navigation.groupMetadata

/**
 * Converts [NavigationState] into decorated [NavEntry]s for [androidx.navigation3.ui.NavDisplay].
 *
 * Destinations render via [NavDestination.Content] unless overridden in [builder].
 * Every entry’s metadata includes [dev.stashy.navigation.GroupIdKey].
 */
@Composable
public fun NavigationState.toDecoratedEntries(
    builder: EntryProviderScope<NavDestination>.() -> Unit = {},
): List<NavEntry<NavDestination>> {
    val provider =
        entryProvider(
            fallback = { destination ->
                NavEntry(destination, metadata = destination.groupMetadata()) {
                    destination.Content()
                }
            },
            builder = builder,
        )
    return toDecoratedEntries(entryProvider = provider)
}

/**
 * Converts [NavigationState] into decorated [NavEntry]s using a fully custom [entryProvider].
 *
 * [dev.stashy.navigation.GroupIdKey] is merged into each entry’s metadata from
 * [NavDestination.group].
 */
@Composable
public fun NavigationState.toDecoratedEntries(
    entryProvider: (NavDestination) -> NavEntry<NavDestination>,
): List<NavEntry<NavDestination>> {
    val providerWithGroup: (NavDestination) -> NavEntry<NavDestination> = { destination ->
        val entry = entryProvider(destination)
        NavEntry(
            key = destination,
            contentKey = entry.contentKey,
            metadata = entry.metadata + destination.groupMetadata(),
        ) {
            entry.Content()
        }
    }

    val groupIds = backStacks.keys.toList()
    val decoratedEntries =
        groupIds.associateWith { groupId: GroupId ->
            key(groupId) {
                val stack = backStacks.getValue(groupId)
                val decorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator<NavDestination>(),
                        rememberViewModelStoreNavEntryDecorator<NavDestination>(),
                    )
                rememberDecoratedNavEntries(
                    backStack = stack,
                    entryDecorators = decorators,
                    entryProvider = providerWithGroup,
                )
            }
        }

    return stacksInUse.flatMap { decoratedEntries[it].orEmpty() }
}
