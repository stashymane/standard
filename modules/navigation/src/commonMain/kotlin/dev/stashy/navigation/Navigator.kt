package dev.stashy.navigation

import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.state.NavigationState
import kotlinx.coroutines.flow.SharedFlow

/**
 * Handles navigation events by updating [state].
 */
public interface Navigator {
    /**
     * The navigation state this navigator mutates.
     */
    public val state: NavigationState

    /**
     * Emits the reselected route when [onReselect] is called (for example a bottom-nav tap on
     * the already selected tab).
     */
    public val reselectEvents: SharedFlow<NavKey>

    /**
     * Navigates to [route].
     */
    public fun navigate(route: NavKey)

    /**
     * Whether [goBack] would change navigation state.
     *
     * @return true if the current top-level stack can pop or the current top-level route is not the
     * start route.
     */
    public fun canGoBack(): Boolean

    /**
     * Goes back.
     */
    public fun goBack()

    /**
     * Signals that [route]’s top-level stack was reselected (for example a bottom-nav tap on the
     * already selected tab).
     */
    public fun onReselect(route: NavKey)

    /**
     * Pops back to the last [route] on any retained stack if that entry is present.
     *
     * Top-level routes are treated as present by definition and simply switch the current stack.
     *
     * @return [Unit] if [route] was found (already current or popped to), or `null` if it is not
     * on any stack.
     */
    public fun popTo(route: NavKey): Unit?
}
