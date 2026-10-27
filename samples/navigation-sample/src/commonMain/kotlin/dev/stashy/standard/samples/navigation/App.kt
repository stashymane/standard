package dev.stashy.standard.samples.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.stashy.navigation.compose.rememberEntryStateNavEntryDecorator
import dev.stashy.navigation.compose.rememberNavigationState
import dev.stashy.navigation.compose.rememberNavigator
import dev.stashy.navigation.ui.rememberNavigatorSceneDecorator
import dev.stashy.standard.samples.navigation.components.BottomBar
import dev.stashy.standard.samples.navigation.components.BreadcrumbBar
import dev.stashy.standard.samples.navigation.scene.ModalBottomSheetSceneStrategy
import dev.stashy.standard.samples.navigation.screens.*

private val topLevelRoutes: Set<Route> = setOf(Route.Home, Route.Search, Route.Profile("me"))

@Composable
fun App() {
    MaterialTheme(
        if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    ) {
        val state = rememberNavigationState(
            startRoute = Route.Home,
            topLevelRoutes = topLevelRoutes,
        )
        val navigator = rememberNavigator<Route>(state)

        Scaffold(
            Modifier.fillMaxSize(),
            topBar = {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    BreadcrumbBar(path = state.backStack)
                    TopAppBar(
                        title = { Text("Navigation Sample") },
                        navigationIcon = {
                            if (navigator.canGoBack()) {
                                IconButton(onClick = { navigator.goBack() }) {
                                    Text("←")
                                }
                            }
                        },
                    )
                }
            },
            bottomBar = {
                BottomBar(
                    current = state.currentStack.firstOrNull { route ->
                        topLevelRoutes.any { it == route }
                    } ?: state.startRoute,
                    onSelect = { navigator.navigate(it) },
                )
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                    sceneStrategies = remember {
                        listOf(ModalBottomSheetSceneStrategy())
                    },
                    entryProvider = entryProvider {
                        entry<Route.Home> { HomeScreen() }
                        entry<Route.Search> { SearchScreen() }
                        entry<Route.SearchResults> { key -> SearchResultsScreen(key.query) }
                        entry<Route.Profile> { key -> ProfileScreen(key.id) }
                        entry<Route.Article> { key -> ArticleScreen(key.slug) }
                        entry<Route.ShareSheet>(
                            metadata = ModalBottomSheetSceneStrategy.bottomSheet()
                        ) { key ->
                            ShareSheetScreen(key.slug, onClose = { navigator.goBack() })
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
