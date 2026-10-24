package dev.stashy.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Default in-memory [Navigator] implementation that directly mutates [state].
 */
public class DefaultNavigator(
    override val state: NavigationState,
) : Navigator {
    override val reselectEvents: SharedFlow<NavKey>
        field = MutableSharedFlow<NavKey>(extraBufferCapacity = 1)

    override fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            state.topLevelRoute = route
            return
        }

        val stack = state.backStacks.getValue(state.topLevelRoute)
        if (stack.lastOrNull() != route) {
            stack.add(route)
        }
    }

    override fun canGoBack(): Boolean {
        val stack = state.backStacks.getValue(state.topLevelRoute)
        return stack.size > 1 || state.topLevelRoute != state.startRoute
    }

    override fun goBack() {
        val stack = state.backStacks.getValue(state.topLevelRoute)
        if (stack.size > 1) {
            stack.removeLastOrNull()
            return
        }

        if (state.topLevelRoute != state.startRoute) {
            state.topLevelRoute = state.startRoute
        }
    }

    override fun onReselect(route: NavKey) {
        reselectEvents.tryEmit(route)
    }

    override fun popTo(route: NavKey): Unit? {
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
