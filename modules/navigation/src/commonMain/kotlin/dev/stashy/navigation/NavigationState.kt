package dev.stashy.navigation

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.util.StableSnapshotListView

/**
 * State holder for hierarchical navigation.
 *
 * @param startRoute the start destination key
 */
public class NavigationState private constructor(
    public val startRoute: NavKey,
    internal val startGroup: NavGroup,
    internal val backStacks: SnapshotStateMap<NavGroup, NavBackStack<NavKey>>,
    public val entryStates: EntryStateStore,
) {
    /**
     * Creates state for [startRoute], resolving the start group from [graph].
     */
    public constructor(startRoute: NavKey, graph: NavGraph<*>) : this(
        startRoute = startRoute,
        startGroup = graph.groupOf(startRoute),
        entryStates = EntryStateStore(),
    )

    private constructor(
        startRoute: NavKey,
        startGroup: NavGroup,
        entryStates: EntryStateStore,
    ) : this(
        startRoute = startRoute,
        startGroup = startGroup,
        backStacks = mutableStateMapOf<NavGroup, NavBackStack<NavKey>>().also { map ->
            map[startGroup] = NavBackStack(startRoute)
        },
        entryStates = entryStates,
    )

    private val activePathState: SnapshotStateList<NavGroup> =
        mutableStateListOf(startGroup)

    internal val activePath: List<NavGroup>
        get() = activePathState.toList()

    internal fun replaceActivePath(path: List<NavGroup>) {
        activePathState.clear()
        activePathState.addAll(path)
    }

    internal fun removeLastActiveGroup() {
        if (activePathState.isNotEmpty()) {
            activePathState.removeAt(activePathState.lastIndex)
        }
    }

    private val entriesSnapshot: List<NavKey> by derivedStateOf {
        activePathState.flatMap { group ->
            backStacks[group].orEmpty()
        }
    }

    /**
     * Flattened destinations along the active group path. Stable identity for
     * [SnapshotFlow][androidx.compose.runtime.snapshotFlow].
     */
    public val entries: List<NavKey> = StableSnapshotListView { entriesSnapshot }

    public val current: NavKey
        get() = entriesSnapshot.lastOrNull() ?: startRoute

    internal val currentGroup: NavGroup
        get() = activePathState.lastOrNull() ?: startGroup
}

private fun NavBackStack<NavKey>?.orEmpty(): List<NavKey> =
    this?.toList().orEmpty()
