# navigation

## Usage

```kotlin
sealed class Route : NavKey {
    data object Home : Route()
    data object Search : Route()
    data class Profile(val id: String) : Route()
    data class Article(val slug: String) : Route()
}

val topLevelRoutes = setOf<NavKey>(Route.Home, Route.Search, Route.Profile("me"))

val state = rememberNavigationState(
    startRoute = Route.Home,
    topLevelRoutes = topLevelRoutes,
)
val navigator = remember { Navigator(state) }

// Compose before NavDisplay so entry state can hydrate from `history.state` on web.
SyncBrowserHistory(
    navigator = navigator,
    pathOf = { /* ... */ },
    parsePath = { /* ... */ },
)

NavDisplay(
    backStack = state.backStack,
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberEntryStateNavEntryDecorator(state),
        rememberViewModelStoreNavEntryDecorator(),
    ),
    sceneDecoratorStrategies = listOf(
        rememberNavigatorSceneDecorator(navigator), // provided within navigation-ui module
    ),
    entryProvider = entryProvider {
        entry<Route.Home> { HomeScreen() }
        entry<Route.Search> { SearchScreen() }
        entry<Route.Profile> { key -> ProfileScreen(key.id) }
        entry<Route.Article> { key -> ArticleScreen(key.slug) }
    },
    onBack = { navigator.goBack() },
)
```
