package dev.stashy.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey

/**
 * A navigable destination that belongs to a [group] and can render itself via [Content].
 *
 * Defaults to [GroupId.Default], which is suitable for the initial / single-stack screen.
 */
public interface NavDestination : NavKey {
    public val group: GroupId
        get() = GroupId.Default

    @Composable
    public fun Content()
}
