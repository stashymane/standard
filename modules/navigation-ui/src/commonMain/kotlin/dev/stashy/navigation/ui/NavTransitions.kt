package dev.stashy.navigation.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.get
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent.SwipeEdge
import dev.stashy.navigation.GroupPeerIndexKey
import dev.stashy.navigation.GroupPeerSetKey

private typealias NavTransitionScope = AnimatedContentTransitionScope<Scene<NavKey>>
private typealias TransitionSpecProvider = NavTransitionScope.() -> ContentTransform
private typealias PredictiveTransitionSpecProvider = NavTransitionScope.(@SwipeEdge Int) -> ContentTransform

/**
 * @param comparePeerIndex compares sibling peer indices (semantically [Comparable.compareTo]).
 * Default is natural order (DSL declaration order within a peer set).
 */
public class NavTransitions(
    public val comparePeerIndex: (from: Int, to: Int) -> Int = Int::compareTo,
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
        val initialSet = initialState.metadata[GroupPeerSetKey]
        val targetSet = targetState.metadata[GroupPeerSetKey]
        if (initialSet == null || targetSet == null || initialSet != targetSet) return 0

        val initialIndex = initialState.metadata[GroupPeerIndexKey] ?: return 0
        val targetIndex = targetState.metadata[GroupPeerIndexKey] ?: return 0
        if (initialIndex == targetIndex) return 0
        return comparePeerIndex(initialIndex, targetIndex)
    }
}
