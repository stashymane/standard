# navigation-ui

UI utilities for Navigation3.

```kotlin
val graph = navGraph<NavKey> {
    group { entry<Home> { } }
    group { entry<Search> { } }
}
// Peer indices follow DSL declaration order (0, 1, …) within each peer set.
val transitions = NavTransitions(
    comparePeerIndex = { from, to -> from.compareTo(to) },
)

NavDisplay(
    backStack = state.entries,
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    ),
    sceneDecoratorStrategies = listOf(
        rememberNavigatorSceneDecorator(navigator),
    ),
    entryProvider = graph.entryProvider,
    onBack = navigator::goBack,
    transitionSpec = transitions.transitionSpec,
    popTransitionSpec = transitions.popTransitionSpec,
    predictivePopTransitionSpec = transitions.predictivePopTransitionSpec,
)
```
