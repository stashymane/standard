package dev.stashy.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The local [Navigator] used within a `NavDisplay`.
 *
 * Provided by `rememberNavigatorSceneDecorator` within the `navigation-ui` module.
 */
public val LocalNavigator: ProvidableCompositionLocal<Navigator> = staticCompositionLocalOf {
    error("No LocalNavigator provided")
}
