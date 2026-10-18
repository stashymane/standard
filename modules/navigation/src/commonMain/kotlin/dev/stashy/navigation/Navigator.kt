package dev.stashy.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

public class Navigator(
    public val state: NavigationState,
    public val graph: NavGraph<*>,
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
        val targetGroup = graph.groupOf(route)
        activateGroup(targetGroup)
        val stack = prepareStack(targetGroup)

        if (stack.isNotEmpty() && stack.first() == route) {
            return
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
     * Whether [goBack] would change navigation state.
     *
     * True when the current group stack can pop, a nested group can be left, or a non-start root
     * peer can return to the start group.
     */
    public fun canGoBack(): Boolean {
        val leaf = state.currentGroup
        val stack = state.backStacks[leaf] ?: return false
        if (stack.size > 1) return true
        val path = state.activePath
        return path.size > 1 || leaf != state.startGroup
    }

    public fun goBack() {
        val leaf = state.currentGroup
        val stack = state.backStacks[leaf] ?: return
        if (stack.size > 1) {
            stack.removeLastOrNull()
            return
        }
        val path = state.activePath
        if (path.size > 1) {
            clearGroupSubtree(leaf)
            state.removeLastActiveGroup()
        } else if (leaf != state.startGroup) {
            state.replaceActivePath(listOf(state.startGroup))
        }
    }

    /**
     * Signals that [route]’s group was reselected (for example a bottom-nav tap on the already
     * selected tab).
     */
    public fun onReselect(route: NavKey) {
        reselectEvents.tryEmit(route)
    }

    /**
     * Pops back to the last [route] on its group’s stack if that entry is present.
     *
     * @return [Unit] if [route] was found (already current or popped to), or `null` if it is not
     * on the stack.
     */
    public fun popTo(route: NavKey): Unit? {
        val targetGroup = if (route == state.startRoute)
            state.startGroup
        else
            graph.groupOf(route)

        val stack = state.backStacks[targetGroup] ?: return null
        val index = stack.lastIndexOf(route)
        if (index < 0) return null

        activateGroup(targetGroup)
        while (stack.lastIndex > index) {
            stack.removeLastOrNull()
        }
        return Unit
    }

    private fun activateGroup(target: NavGroup) {
        val desired = target.pathFromRoot()
        state.replaceActivePath(desired)
        prepareStack(target)
    }

    private fun clearGroupSubtree(group: NavGroup) {
        state.backStacks.remove(group)
        for (descendant in group.descendants()) {
            state.backStacks.remove(descendant)
        }
    }

    private fun prepareStack(group: NavGroup): NavBackStack<NavKey> =
        state.backStacks.getOrPut(group) { NavBackStack() }
}
