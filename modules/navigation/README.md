# navigation

A module for Jetpack Navigation 3 with multiple group back stacks and web URL sync.

## Usage

```kotlin
val state = rememberNavigationState(startRoute = Home)
val navigator = remember { Navigator(state) }

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
    entryProvider = state::toEntry,
    onBack = { navigator.goBack() },
)
```

Destinations:

```kotlin
@Serializable
data object Home : NavDestination {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        var query by rememberNavEntryState() { "" } // saves state to the navigation entry
        HomeScreen(query = query, onQueryChange = { query = it }, onOpen = { navigator.navigate(Details) })
    }
}
```
