package dev.stashy.navigation.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.stashy.navigation.Navigator

/**
 * The local [dev.stashy.navigation.Navigator] used within a `NavDisplay`.
 *
 * Provided by `rememberNavigatorSceneDecorator`.
 */
public val LocalNavigator: ProvidableCompositionLocal<Navigator> = staticCompositionLocalOf {
    error("No LocalNavigator provided")
}
