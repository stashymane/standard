package dev.stashy.navigation.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.ui.unit.IntOffset
import androidx.navigationevent.NavigationEvent.Companion.EDGE_LEFT
import androidx.navigationevent.NavigationEvent.Companion.EDGE_RIGHT
import androidx.navigationevent.NavigationEvent.SwipeEdge
import dev.stashy.standard.ui.animation.tweenInstantOut

@Suppress("ConstPropertyName")
internal object Defaults {
    internal val Animation: FiniteAnimationSpec<Float> = tweenInstantOut()
    internal val OffsetAnimation: FiniteAnimationSpec<IntOffset> = tweenInstantOut()

    internal const val InitialScale: Float = 0.95f
    internal const val TargetScale: Float = 1.05f

    internal const val PredictiveScale: Float = 0.95f
    internal const val PredictiveOffset: Float = 1f / 8f

    internal const val GroupOffset: Float = 1f / 16f
}

public fun navEnter(
    initialScale: Float,
    animationSpec: FiniteAnimationSpec<Float> = Defaults.Animation,
): EnterTransition = fadeIn(animationSpec) + scaleIn(animationSpec, initialScale = initialScale)

public fun navExit(
    targetScale: Float,
    animationSpec: FiniteAnimationSpec<Float> = Defaults.Animation,
): ExitTransition = fadeOut(animationSpec) + scaleOut(animationSpec, targetScale = targetScale)

public fun navForward(
    initialScale: Float = Defaults.InitialScale,
    targetScale: Float = Defaults.TargetScale,
    animationSpec: FiniteAnimationSpec<Float> = Defaults.Animation,
): ContentTransform = ContentTransform(
    navEnter(initialScale, animationSpec),
    navExit(targetScale, animationSpec),
)

public fun navBackward(
    initialScale: Float = Defaults.InitialScale,
    targetScale: Float = Defaults.TargetScale,
    animationSpec: FiniteAnimationSpec<Float> = Defaults.Animation,
): ContentTransform = ContentTransform(
    navEnter(targetScale, animationSpec),
    navExit(initialScale, animationSpec),
)

/**
 * Scale-out exit that also shifts along the predictive back swipe direction
 * ([EDGE_LEFT] → right, [EDGE_RIGHT] → left).
 */
public fun navExitPredictive(
    @SwipeEdge edge: Int,
    targetScale: Float = Defaults.PredictiveScale,
    offsetFraction: Float = Defaults.PredictiveOffset,
    animationSpec: FiniteAnimationSpec<Float> = Defaults.Animation,
    offsetAnimationSpec: FiniteAnimationSpec<IntOffset> = Defaults.OffsetAnimation,
): ExitTransition {
    val scaleExit = navExit(targetScale, animationSpec)
    val signedOffset = when (edge) {
        EDGE_LEFT -> offsetFraction
        EDGE_RIGHT -> -offsetFraction
        else -> return scaleExit
    }
    return scaleExit + slideOutHorizontally(offsetAnimationSpec) { (it * signedOffset).toInt() }
}

/** Predictive-pop transform: fade in, scale out, and slide with [edge]. */
public fun navPredictive(
    @SwipeEdge edge: Int,
    targetScale: Float = Defaults.PredictiveScale,
    offsetFraction: Float = Defaults.PredictiveOffset,
    animationSpec: FiniteAnimationSpec<Float> = Defaults.Animation,
    offsetAnimationSpec: FiniteAnimationSpec<IntOffset> = Defaults.OffsetAnimation,
): ContentTransform = ContentTransform(
    fadeIn(animationSpec),
    navExitPredictive(edge, targetScale, offsetFraction, animationSpec, offsetAnimationSpec),
)

public fun navGroupEnter(
    offsetFraction: Float,
    animationSpec: FiniteAnimationSpec<Float> = Defaults.Animation,
    offsetAnimationSpec: FiniteAnimationSpec<IntOffset> = Defaults.OffsetAnimation,
): EnterTransition = fadeIn(animationSpec) + slideInHorizontally(offsetAnimationSpec) {
    (it * offsetFraction).toInt()
}

public fun navGroupExit(
    offsetFraction: Float,
    animationSpec: FiniteAnimationSpec<Float> = Defaults.Animation,
    offsetAnimationSpec: FiniteAnimationSpec<IntOffset> = Defaults.OffsetAnimation,
): ExitTransition = fadeOut(animationSpec) + slideOutHorizontally(offsetAnimationSpec) {
    (it * offsetFraction).toInt()
}

/**
 * Group switch toward a higher order: enter from [offsetFraction] left, exit to the right.
 *
 * @see navGroupLeft
 */
public fun navGroupRight(
    offsetFraction: Float = Defaults.GroupOffset,
    animationSpec: FiniteAnimationSpec<Float> = Defaults.Animation,
    offsetAnimationSpec: FiniteAnimationSpec<IntOffset> = Defaults.OffsetAnimation,
): ContentTransform = ContentTransform(
    navGroupEnter(-offsetFraction, animationSpec, offsetAnimationSpec),
    navGroupExit(offsetFraction, animationSpec, offsetAnimationSpec),
)

/**
 * Group switch toward a lower order: same [offsetFraction] as [navGroupRight], signs swapped.
 */
public fun navGroupLeft(
    offsetFraction: Float = Defaults.GroupOffset,
    animationSpec: FiniteAnimationSpec<Float> = Defaults.Animation,
    offsetAnimationSpec: FiniteAnimationSpec<IntOffset> = Defaults.OffsetAnimation,
): ContentTransform = ContentTransform(
    navGroupEnter(offsetFraction, animationSpec, offsetAnimationSpec),
    navGroupExit(-offsetFraction, animationSpec, offsetAnimationSpec),
)

public val NavEnterForward: EnterTransition = navEnter(Defaults.InitialScale)
public val NavExitForward: ExitTransition = navExit(Defaults.TargetScale)
public val NavEnterBackward: EnterTransition = navEnter(Defaults.TargetScale)
public val NavExitBackward: ExitTransition = navExit(Defaults.InitialScale)
public val NavEnterPredictive: EnterTransition = fadeIn(Defaults.Animation)

public val NavGroupEnterRight: EnterTransition = navGroupEnter(-Defaults.GroupOffset)
public val NavGroupEnterLeft: EnterTransition = navGroupEnter(Defaults.GroupOffset)
public val NavGroupExitRight: ExitTransition = navGroupExit(Defaults.GroupOffset)
public val NavGroupExitLeft: ExitTransition = navGroupExit(-Defaults.GroupOffset)

public val NavForward: ContentTransform = navForward()
public val NavBackward: ContentTransform = navBackward()
public val NavGroupRight: ContentTransform = navGroupRight()
public val NavGroupLeft: ContentTransform = navGroupLeft()
