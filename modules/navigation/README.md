# navigation

## Usage

```kotlin
val graph = navGraph<NavKey> {
    group {
        entry<Home> { HomeScreen() }
        entry<HomeDetail> { key -> HomeDetailScreen(key) }
    }
    group {
        entry<Search> { SearchScreen() }
    }
    group {
        entry<Profile> { ProfileScreen() }
        group {
            entry<Posts> { PostsScreen() }
            entry<PostDetail> { key -> PostDetailScreen(key) }
        }
    }
}

val state = rememberNavigationState(startRoute = Home, graph = graph)
val navigator = remember { Navigator(state, graph) }

// Compose before NavDisplay so entry state can hydrate from `history.state` on web.
SyncBrowserHistory(
    navigator = navigator,
    pathOf = { /* ... */ },
    parsePath = { /* ... */ },
)

NavDisplay(
    backStack = state.entries,
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberEntryStateNavEntryDecorator(state),
        rememberViewModelStoreNavEntryDecorator(),
    ),
    sceneDecoratorStrategies = listOf(
        rememberNavigatorSceneDecorator(navigator), // provided within navigation-ui module
    ),
    entryProvider = graph.entryProvider,
    onBack = { navigator.goBack() },
)
```
