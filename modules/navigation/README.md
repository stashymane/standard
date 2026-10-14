# navigation

A module for Jetpack Navigation 3 with multiple group back stacks and web URL sync.

## Usage

```kotlin
val state = rememberNavigationState(startRoute = Home)
val navigator = remember { Navigator(state) }

CompositionLocalProvider(LocalNavigator provides navigator) {
    SyncBrowserHistory(
        navigator = navigator,
        pathOf = { /* ... */ },
        parsePath = { /* ... */ },
    )

    // NavDisplay comes from navigation3-ui (app dependency)
    NavDisplay(
        entries = state.toDecoratedEntries {
            // optional manual overrides; everything else uses NavDestination.Content()
            entry<Legacy> { LegacyScreen() }
        },
        onBack = { navigator.goBack() },
    )
}
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
