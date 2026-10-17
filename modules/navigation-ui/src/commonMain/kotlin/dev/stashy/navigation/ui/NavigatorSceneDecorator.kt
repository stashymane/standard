package dev.stashy.navigation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import dev.stashy.navigation.LocalNavigator
import dev.stashy.navigation.Navigator

@Composable
public fun <T : Any> rememberNavigatorSceneDecorator(
    navigator: Navigator,
): SceneDecoratorStrategy<T> = remember(navigator) {
    SceneDecoratorStrategy { scene ->
        NavigatorDecoratedScene(scene = scene, navigator = navigator)
    }
}

private data class NavigatorDecoratedScene<T : Any>(
    private val scene: Scene<T>,
    private val navigator: Navigator,
) : Scene<T> by scene {
    override val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalNavigator provides navigator, content = scene.content)
    }
}
