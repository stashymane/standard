package dev.stashy.navigation

import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Handles navigation events by updating [NavigationState].
 */
public class Navigator(
    public val state: NavigationState,
) {
    public val reselectEvents: SharedFlow<GroupId>
        field = MutableSharedFlow<GroupId>(extraBufferCapacity = 1)

    /**
     * Navigates to [route].
     *
     * - [NavDestination.Group] destinations switch to that group when it differs from the current
     *   group. Navigating to a group’s existing root only switches the active group.
     * - Non-group destinations are pushed onto the current group’s back stack.
     */
    public fun navigate(route: NavDestination) {
        val targetGroup = resolveGroupFor(route)
        val stack = prepareStack(targetGroup)

        if (route is NavDestination.Group) {
            state.currentGroup = targetGroup

            // Group root: switch to this group and keep its existing stack.
            if (stack.isNotEmpty() && stack.first() == route) {
                return
            }
        }

        if (stack.isEmpty()) {
            stack.add(route)
            return
        }

        if (stack.lastOrNull() != route) {
            stack.add(route)
        }
    }

    /**
     * Pops within the current group, or switches back to the start group when at that group’s root
     * (“exit through home”).
     */
    public fun goBack() {
        val stack = state.backStacks[state.currentGroup] ?: return
        if (stack.size > 1) {
            stack.removeLastOrNull()
        } else if (state.currentGroup != state.startGroup) {
            state.currentGroup = state.startGroup
        }
    }

    /**
     * Signals that [group] was reselected (for example a bottom-nav tap on the already selected tab).
     */
    public fun onReselect(group: GroupId) {
        reselectEvents.tryEmit(group)
    }

    /**
     * Browser-history style sync: switch to [route]’s group (or the current group for non-group
     * routes), pop back to [route] if it is already on that stack, otherwise push it.
     *
     * The start route always restores the start group and truncates that stack.
     */
    public fun popToOrNavigate(route: NavDestination) {
        if (route == state.startRoute) {
            val stack = prepareStack(state.startGroup)
            state.currentGroup = state.startGroup
            while (stack.size > 1) {
                stack.removeLastOrNull()
            }
            if (stack.isEmpty()) {
                stack.add(state.startRoute)
            } else if (stack.first() != state.startRoute) {
                stack.clear()
                stack.add(state.startRoute)
            }
            return
        }

        val targetGroup = resolveGroupFor(route)
        val stack = prepareStack(targetGroup)

        if (route is NavDestination.Group) {
            state.currentGroup = targetGroup
        }

        if (stack.lastOrNull() == route) return

        val index = stack.lastIndexOf(route)
        if (index >= 0) {
            while (stack.lastIndex > index) {
                stack.removeLastOrNull()
            }
        } else {
            stack.add(route)
        }
    }

    /**
     * Group destinations use their [NavDestination.Group.group]; others use the current group.
     */
    private fun resolveGroupFor(route: NavDestination): GroupId =
        (route as? NavDestination.Group)?.group ?: state.currentGroup

    private fun prepareStack(group: GroupId): NavBackStack<NavDestination> =
        state.backStacks.getOrPut(group) { NavBackStack() }
}
