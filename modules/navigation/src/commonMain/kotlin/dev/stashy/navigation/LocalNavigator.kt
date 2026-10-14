package dev.stashy.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Provides the [Navigator] to [NavDestination.Content] and other UI without plumbing it through
 * every destination.
 */
public val LocalNavigator: ProvidableCompositionLocal<Navigator> =
    staticCompositionLocalOf {
        error(
            "No Navigator provided. Wrap your hierarchy in " +
                "CompositionLocalProvider(LocalNavigator provides navigator).",
        )
    }
