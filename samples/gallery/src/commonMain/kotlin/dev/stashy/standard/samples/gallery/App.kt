package dev.stashy.standard.samples.gallery

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.stashy.navigation.compose.rememberEntryStateNavEntryDecorator
import dev.stashy.navigation.compose.rememberNavigationState
import dev.stashy.navigation.compose.rememberNavigator
import dev.stashy.navigation.ui.rememberNavigatorSceneDecorator
import dev.stashy.standard.samples.gallery.screens.home.HomeScreen
import dev.stashy.standard.samples.gallery.sidebar.Sidebar
import dev.stashy.standard.samples.gallery.theme.GalleryTheme
import dev.stashy.standard.ui.components.scroll.LocalScrollbarStyle
import dev.stashy.standard.ui.components.scroll.ScrollbarStyle
import dev.stashy.standard.ui.responsive.LocalResponsiveInfo
import dev.stashy.standard.ui.responsive.rememberResponsiveInfo
import org.koin.compose.KoinApplication
import org.koin.compose.getKoin
import org.koin.plugin.module.dsl.koinConfiguration

@Composable
fun App() {
    KoinApplication(configuration = koinConfiguration<GalleryApp>()) {
        var darkTheme by rememberSaveable { mutableStateOf(true) }
        val responsiveInfo = rememberResponsiveInfo()
        
        GalleryTheme(darkTheme = darkTheme) {
            val scrollbarStyle = ScrollbarStyle.default()

            CompositionLocalProvider(
                LocalResponsiveInfo provides responsiveInfo,
                LocalScrollbarStyle provides scrollbarStyle
            ) {
                Gallery(
                    darkTheme = darkTheme,
                    onDarkThemeChange = { darkTheme = it },
                )
            }
        }
    }
}

@Composable
private fun Gallery(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
) {
    val koin = getKoin()
    val screens = remember(koin) {
        koin.getAll<GalleryScreen>().sortedBy { it.title }
    }

    val state = rememberNavigationState<GalleryRoute>(
        startRoute = GalleryRoute.Home,
        topLevelRoutes = setOf(GalleryRoute.Home),
    )
    val navigator = rememberNavigator<GalleryRoute>(state)
    val selectedScreenId = (state.backStack.lastOrNull() as? GalleryRoute.Sample)?.id

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(
                screens = screens,
                selectedScreenId = selectedScreenId,
                canGoBack = navigator.canGoBack(),
                onBack = { navigator.goBack() },
                onSelect = { screen ->
                    if (screen.id != selectedScreenId) {
                        navigator.navigate(GalleryRoute.Sample(screen.id))
                    }
                },
                darkTheme = darkTheme,
                onDarkThemeChange = onDarkThemeChange,
                modifier = Modifier.width(280.dp).fillMaxHeight(),
            )

            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )

            Box(Modifier.weight(1f).fillMaxHeight()) {
                NavDisplay(
                    backStack = state.backStack,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberEntryStateNavEntryDecorator(state),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    sceneDecoratorStrategies = listOf(
                        rememberNavigatorSceneDecorator(navigator),
                    ),
                    entryProvider = entryProvider {
                        entry<GalleryRoute.Home> { HomeScreen() }
                        entry<GalleryRoute.Sample> { route ->
                            val screen = screens.firstOrNull { it.id == route.id }
                            if (screen == null) MissingSample(route.id) else screen.Content()
                        }
                    },
                    onBack = { navigator.goBack() },
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(220))
                    },
                )
            }
        }
    }
}

@Composable
private fun MissingSample(id: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text("No sample registered for \"$id\"")
    }
}

@Preview
@Composable
private fun MissingSamplePreview() {
    GalleryTheme {
        MissingSample("missing-id")
    }
}
