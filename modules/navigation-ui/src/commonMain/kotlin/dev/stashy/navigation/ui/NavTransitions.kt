package dev.stashy.navigation.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent.SwipeEdge
import dev.stashy.navigation.GroupId
import dev.stashy.navigation.GroupIdKey
import dev.stashy.navigation.NavDestination

private typealias NavTransitionScope = AnimatedContentTransitionScope<Scene<NavDestination>>
private typealias TransitionSpecProvider = NavTransitionScope.() -> ContentTransform
private typealias PredictiveTransitionSpecProvider = NavTransitionScope.(@SwipeEdge Int) -> ContentTransform

/**
 * @param compareGroups compares two groups by their [GroupId].
 * Semantically the same as [Comparable.compareTo].
 */
public class NavTransitions(
    public val compareGroups: (from: GroupId, to: GroupId) -> Int = { _, _ -> 0 },
    public val groupRight: ContentTransform = NavGroupRight,
    public val groupLeft: ContentTransform = NavGroupLeft,
) {
    public val transitionSpec: TransitionSpecProvider = { transition(back = false) }
    public val popTransitionSpec: TransitionSpecProvider = { transition(back = true) }
    public val predictivePopTransitionSpec: PredictiveTransitionSpecProvider = { edge -> predictiveTransition(edge) }

    public fun NavTransitionScope.transition(
        back: Boolean = false,
    ): ContentTransform {
        val direction = currentGroupDirection()

        return when {
            direction > 0 -> groupRight
            direction < 0 -> groupLeft
            else -> if (back) NavBackward else NavForward
        }
    }

    /**
     * Predictive back transform. Group switches keep the directional slide; same-group pops use
     * [navPredictive] (scale-out + slide along [edge]).
     */
    public fun NavTransitionScope.predictiveTransition(
        @SwipeEdge edge: Int,
    ): ContentTransform {
        val direction = currentGroupDirection()

        return when {
            direction > 0 -> groupRight
            direction < 0 -> groupLeft
            else -> navPredictive(edge)
        }
    }

    private fun NavTransitionScope.currentGroupDirection(): Int {
        val initial = initialState.metadata[GroupIdKey]
        val target = targetState.metadata[GroupIdKey]
        return groupDirection(initial, target)
    }

    private fun groupDirection(initial: GroupId?, target: GroupId?): Int {
        if (initial == null || target == null || initial == target) return 0
        return compareGroups(initial, target)
    }
}
