package dev.stashy.navigation.compose

import androidx.navigation3.runtime.NavEntry
import dev.stashy.navigation.GroupId
import dev.stashy.navigation.NavDestination
import dev.stashy.navigation.NavigationState
import dev.stashy.navigation.groupMetadata

/**
 * Builds a [NavEntry] for [destination], rendering [NavDestination.Content].
 *
 * Metadata includes [dev.stashy.navigation.GroupIdKey] for the stack the destination lives on.
 *
 * Use as [androidx.navigation3.ui.NavDisplay]’s `entryProvider` (e.g. `entryProvider = state::toEntry`).
 * For unknown / 404-like keys, supply a `fallback` to androidx’s `entryProvider { }` DSL — do not
 * put main content there.
 */
public fun NavigationState.toEntry(destination: NavDestination): NavEntry<NavDestination> =
    NavEntry(
        key = destination,
        metadata = groupMetadata(groupIdOf(destination)),
    ) {
        destination.Content()
    }

/**
 * [GroupId] of the stack in [stacksInUse][NavigationState.stacksInUse] that contains
 * [destination], or the destination’s own group / [NavigationState.currentGroup] as fallback.
 */
public fun NavigationState.groupIdOf(destination: NavDestination): GroupId {
    for (groupId in stacksInUse) {
        val stack = backStacks[groupId] ?: continue
        if (destination in stack) return groupId
    }
    return (destination as? NavDestination.Group)?.group ?: currentGroup
}
