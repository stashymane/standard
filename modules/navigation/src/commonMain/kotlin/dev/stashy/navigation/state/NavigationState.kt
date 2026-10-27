package dev.stashy.navigation.state

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.OverlayNavKey

/**
 * State holder for navigation.
 *
 * Each top-level route owns a retained [NavBackStack]. The visible [backStack] always contains the
 * start route's stack, followed by the current top-level route's stack when it differs (the
 * "exit through home" pattern).
 *
 * @param startRoute the route the user exits through; must be a key in [backStacks]
 * @param topLevelRoute the state backing the currently selected top-level route
 * @param backStacks the retained back stack for each top-level route
 * @param entryStates optional store for per-entry state
 */
public class NavigationState(
    public val startRoute: NavKey,
    topLevelRoute: MutableState<NavKey>,
    internal val backStacks: Map<NavKey, NavBackStack<NavKey>>,
    public val entryStates: EntryStateStore = EntryStateStore(),
) {
    private val topLevelRouteState: MutableState<NavKey> = topLevelRoute

    init {
        require(startRoute in backStacks) {
            "startRoute ($startRoute) must be a top-level route"
        }
        require(topLevelRouteState.value in backStacks) {
            "topLevelRoute (${topLevelRouteState.value}) must be present within backStacks"
        }
        backStacks.keys.filterIsInstance<OverlayNavKey>().let { overlays ->
            require(overlays.isEmpty()) { "Overlays cannot be top level routes: $overlays" }
        }
    }

    /**
     * The currently selected top-level route.
     */
    public var topLevelRoute: NavKey
        get() = topLevelRouteState.value
        set(value) {
            require(value in backStacks) {
                "Cannot switch to route ($value) that is not a top-level route"
            }
            topLevelRouteState.value = value
        }

    /**
     * The entire currently visible back stack.
     * This always includes the initial backStack.
     */
    public val backStack: List<NavKey> by derivedStateOf {
        buildList {
            addAll(backStacks.getValue(startRoute))
            val current = topLevelRouteState.value
            if (current != startRoute) {
                addAll(backStacks.getValue(current))
            }
        }
    }

    /**
     * The current top-level route's back stack.
     */
    public val currentStack: List<NavKey>
        get() = backStacks.getValue(topLevelRoute)

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
