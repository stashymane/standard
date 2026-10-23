package dev.stashy.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Handles navigation events by updating [state].
 */
public class Navigator(
    public val state: NavigationState,
) {
    /**
     * Emits the reselected route when [onReselect] is called (for example a bottom-nav tap on
     * the already selected tab).
     */
    public val reselectEvents: SharedFlow<NavKey>
        field = MutableSharedFlow<NavKey>(extraBufferCapacity = 1)

    /**
     * Navigates to [route].
     */
    public fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
            return
        }

        val stack = state.backStacks.getValue(state.topLevelRoute)
        if (stack.lastOrNull() != route) {
            stack.add(route)
        }
    }

    /**
     * Whether [goBack] would change navigation state.
     *
     * @return true if the current top-level stack can pop or the current top-level route is not the
     * start route.
     */
    public fun canGoBack(): Boolean {
        val stack = state.backStacks.getValue(state.topLevelRoute)
        return stack.size > 1 || state.topLevelRoute != state.startRoute
    }

    /**
     * Goes back.
     */
    public fun goBack() {
        val stack = state.backStacks.getValue(state.topLevelRoute)
        if (stack.size > 1) {
            stack.removeLastOrNull()
            return
        }

        if (state.topLevelRoute != state.startRoute) {
            state.topLevelRoute = state.startRoute
        }
    }

    /**
     * Signals that [route]’s top-level stack was reselected (for example a bottom-nav tap on the
     * already selected tab).
     */
    public fun onReselect(route: NavKey) {
        reselectEvents.tryEmit(route)
    }

    /**
     * Pops back to the last [route] on any retained stack if that entry is present.
     *
     * Top-level routes are treated as present by definition and simply switch the current stack.
     *
     * @return [Unit] if [route] was found (already current or popped to), or `null` if it is not
     * on any stack.
     */
    public fun popTo(route: NavKey): Unit? {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
            return Unit
        }

        for ((key, stack) in state.backStacks) {
            val index = stack.lastIndexOf(route)
            if (index < 0) continue

            state.topLevelRoute = key
            while (stack.lastIndex > index) {
                stack.removeLastOrNull()
            }
            return Unit
        }

        return null
    }
}
