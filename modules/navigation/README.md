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
val navigator = rememberNavigator<Route>(state)

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

## Overlay destinations

Overlay destinations are ephemeral and never addressable. Declare them with `OverlayNavKey`:

```kotlin
data class SomeDialog(val value: String) : OverlayNavKey
```

```kotlin
navigator.navigate(SomeDialog("foo"))
```

Overlays are pushed onto the current stack and can be dismissed with `goBack()`. Navigating to any
other route dismisses them automatically, and switching top-level stacks discards them so they never
reappear. On the web, the URL keeps showing the last non-overlay entry, and URL/deeplink resolution
never produces an overlay route.

To render an overlay above the current screen, you may use Navigation3's overlay scene support, e.g.:

```kotlin
NavDisplay(
    // ...
    sceneStrategies = listOf(DialogSceneStrategy()),
    entryProvider = entryProvider {
        entry<Route.ShareSheet>(metadata = DialogSceneStrategy.dialog()) { key ->
            SomeDialogContent(key.value, onClose = { navigator.goBack() })
        }
    },
)
```

Note that Navigation3 does not apply scene decorators to overlay scenes, so `LocalNavigator` is not available
inside content rendered by `DialogSceneStrategy`.
