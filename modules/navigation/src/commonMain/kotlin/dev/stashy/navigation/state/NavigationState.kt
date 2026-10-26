package dev.stashy.navigation.state

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.OverlayNavKey
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
    topLevelRoutes: Set<NavKey>,
    public val entryStates: EntryStateStore = EntryStateStore(),
) {
    init {
        require(startRoute in topLevelRoutes) {
            "startRoute ($startRoute) must be in topLevelRoutes"
        }
        topLevelRoutes.filterIsInstance<OverlayNavKey>().let { overlays ->
            require(overlays.isEmpty()) { "Overlays cannot be top level routes: $overlays" }
        }
    }

    private val topLevelRouteState = mutableStateOf(startRoute)

    /**
     * The currently selected top-level route.
     */
    internal var topLevelRoute: NavKey
        get() = topLevelRouteState.value
        set(value) {
            require(value in backStacks.keys) {
                "Cannot switch to route ($value) that is not a top-level route"
            }
            topLevelRouteState.value = value
        }

    internal val backStacks: Map<NavKey, NavBackStack<NavKey>> = topLevelRoutes.associateWith { key ->
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

    private val currentSnapshot: List<NavKey> by derivedStateOf {
        val stack = backStacks.getValue(topLevelRoute)
        stack.toList()
    }

    /**
     * The entire currently visible back stack.
     */
    public val backStack: List<NavKey> = StableSnapshotListView { backStackSnapshot }

    /**
     * The current top-level route's back stack.
     */
    public val current: List<NavKey> = StableSnapshotListView { currentSnapshot }

    /**
     * Removes all overlay routes from retained stacks.
     *
     * Overlays are only valid as a contiguous suffix of the current top-level stack, so this keeps
     * them from surviving stack switches.
     */
    internal fun discardOverlays() {
        backStacks.values.forEach { stack ->
            while (stack.lastOrNull() is OverlayNavKey) {
                stack.removeLastOrNull()
            }
        }
    }

    /**
     * The most recent visible route that is not an [OverlayNavKey].
     */
    internal fun lastNonOverlayRoute(): NavKey =
        backStack.lastOrNull { it !is OverlayNavKey } ?: startRoute
}
