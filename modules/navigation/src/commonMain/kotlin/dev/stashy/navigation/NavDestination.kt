package dev.stashy.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey

/**
 * A navigable destination that can render itself via [Content].
 *
 * Destinations that participate in multi-group (e.g. tab) navigation should implement [Group].
 * Non-group destinations are pushed onto whichever back stack is currently active.
 */
public interface NavDestination : NavKey {
    @Composable
    public fun Content()

    /**
     * A destination that belongs to a navigation [group].
     *
     * Defaults to [GroupId.Default], which is suitable for the initial / single-stack screen.
     */
    public interface Group : NavDestination {
        public val group: GroupId
            get() = GroupId.Default
    }
}
