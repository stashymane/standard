package dev.stashy.standard.ui.animation

import androidx.compose.animation.core.AnimationConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

public val FastOutEasing: CubicBezierEasing = CubicBezierEasing(0f, .4f, .4f, 1f)
public val FastInEasing: CubicBezierEasing = CubicBezierEasing(0.4f, 0f, 1f, 0.4f)

public val InstantOutEasing: CubicBezierEasing = CubicBezierEasing(0f, .6f, .2f, 1f)
public val InstantInEasing: CubicBezierEasing = CubicBezierEasing(0.6f, 0f, 1f, 0.2f)

public fun <T> tweenFastOut(
    durationMillis: Int = AnimationConstants.DefaultDurationMillis,
    delayMillis: Int = 0
): TweenSpec<T> =
    tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = FastOutEasing)

public fun <T> tweenFastIn(
    durationMillis: Int = AnimationConstants.DefaultDurationMillis,
    delayMillis: Int = 0
): TweenSpec<T> =
    tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = FastInEasing)

public fun <T> tweenInstantOut(
    durationMillis: Int = AnimationConstants.DefaultDurationMillis,
    delayMillis: Int = 0
): TweenSpec<T> =
    tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = InstantOutEasing)

public fun <T> tweenInstantIn(
    durationMillis: Int = AnimationConstants.DefaultDurationMillis,
    delayMillis: Int = 0
): TweenSpec<T> =
    tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = InstantInEasing)
