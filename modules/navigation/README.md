# navigation

A module for Jetpack Navigation 3 with multiple group back stacks and web URL sync.

## Usage

```kotlin
val state = rememberNavigationState(startRoute = Home)
val navigator = remember { Navigator(state) }

SyncBrowserHistory(
    navigator = navigator,
    pathOf = { /* ... */ },
    parsePath = { /* ... */ },
)

NavDisplay(
    backStack = state.entries,
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
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
    // group defaults to GroupId.Default — override for multi-tab stacks:
    // override val group = GroupId("home")

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        HomeScreen(onOpen = { navigator.navigate(Details) })
    }
}
```
