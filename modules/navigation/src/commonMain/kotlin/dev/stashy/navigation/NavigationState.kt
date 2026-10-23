package dev.stashy.navigation

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.util.StableSnapshotListView

/**
 * State holder for navigation.
 *
 * Each top-level route owns a retained [NavBackStack]. The visible [backStack] always contains the
 * start route's stack, followed by the current top-level route's stack when it differs (the
 * "exit through home" pattern).
 *
 * @param startRoute the route the user exits through; must be in [topLevelRoutes]
 * @param topLevelRoutes the top-level routes that can be switched between
 * @param entryStates optional store for per-entry state
 */
public class NavigationState(
    public val startRoute: NavKey,
    public val topLevelRoutes: Set<NavKey>,
    public val entryStates: EntryStateStore = EntryStateStore(),
) {
    init {
        require(startRoute in topLevelRoutes) {
            "startRoute ($startRoute) must be in topLevelRoutes"
        }
    }

    private val topLevelRouteState = mutableStateOf(startRoute)

    /**
     * The currently selected top-level route.
     */
    public var topLevelRoute: NavKey
        get() = topLevelRouteState.value
        internal set(value) {
            require(value in topLevelRoutes) {
                "Cannot switch to route ($value) that is not in topLevelRoutes"
            }
            topLevelRouteState.value = value
        }

    public val backStacks: Map<NavKey, NavBackStack<NavKey>> = topLevelRoutes.associateWith { key ->
        NavBackStack(key)
    }

    private val backStackSnapshot: List<NavKey> by derivedStateOf {
        buildList {
            addAll(backStacks.getValue(startRoute))
            val current = topLevelRoute
            if (current != startRoute) {
                addAll(backStacks.getValue(current))
            }
        }
    }

    /**
     * The currently visible back stack.
     */
    public val backStack: List<NavKey> = StableSnapshotListView { backStackSnapshot }

    /**
     * The current destination, i.e. the last entry in [backStack].
     */
    public val current: NavKey
        get() = backStack.lastOrNull() ?: startRoute
}
