package dev.stashy.standard.ui.gradients

import androidx.compose.animation.core.Easing
import com.github.ajalt.colormath.transform.EasingFunction

public fun Easing.toEasingFunction(): EasingFunction = EasingFunction(::transform)
public fun EasingFunction.toComposeEasing(): Easing = Easing(::ease)
