package dev.stashy.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.navigation3.runtime.NavBackStack

/**
 * State holder for multi-group navigation.
 *
 * This class does not modify its own state. It is designed to be modified using [Navigator].
 *
 * Group stacks are created lazily when first targeted. [stacksInUse] follows the
 * “exit through home” pattern: the start group is always present, plus at most one other
 * current group.
 *
 * @param startRoute the start destination; when it is a [NavDestination.Group] its group is used,
 * otherwise [GroupId.Default]
 * @param currentGroup the state object that backs the active group
 * @param backStacks per-group back stacks (start group is initialized with [startRoute])
 */
public class NavigationState(
    public val startRoute: NavDestination,
    public val backStacks: SnapshotStateMap<GroupId, NavBackStack<NavDestination>> =
        mutableStateMapOf<GroupId, NavBackStack<NavDestination>>().also { map ->
            map[startRoute.startGroup()] = NavBackStack(startRoute)
        },
) {
    public var currentGroup: GroupId by mutableStateOf(startRoute.startGroup())

    public val startGroup: GroupId = startRoute.startGroup()

    /**
     * Groups whose stacks should be shown. The start group is always first; when another group
     * is active it is appended (at most one other group).
     */
    public val stacksInUse: List<GroupId>
        get() = if (currentGroup == startGroup) {
            listOf(startGroup)
        } else {
            listOf(startGroup, currentGroup)
        }

    private val entriesSnapshot: List<NavDestination> by derivedStateOf {
        stacksInUse.flatMap { groupId ->
            backStacks[groupId].orEmpty()
        }
    }

    /**
     * Flattened destinations for [stacksInUse]. Stable identity for [androidx.compose.runtime.snapshotFlow].
     */
    public val entries: List<NavDestination> = StableSnapshotListView { entriesSnapshot }

    public val current: NavDestination
        get() = entriesSnapshot.lastOrNull() ?: startRoute
}

/**
 * Group of [startRoute] when it is a [NavDestination.Group], otherwise [GroupId.Default].
 */
internal fun NavDestination.startGroup(): GroupId =
    (this as? NavDestination.Group)?.group ?: GroupId.Default

private fun NavBackStack<NavDestination>?.orEmpty(): List<NavDestination> =
    this?.toList().orEmpty()
