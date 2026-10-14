package dev.stashy.navigation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.stashy.navigation.LocalNavigator
import dev.stashy.navigation.Navigator

/**
 * Provides this as a [LocalNavigator] within [content].
 */
@Composable
public fun Navigator.Provide(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavigator provides this, content = content)
}
