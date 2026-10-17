# navigation-ui

UI utilities for Navigation3.

```kotlin
val order = listOf(Projects.group, Media.group, About.group)
val transitions = NavTransitions(
    compareGroups = { from, to -> order.indexOf(from).compareTo(order.indexOf(to)) },
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
    entryProvider = state::toEntry,
    onBack = navigator::goBack,
    transitionSpec = transitions.transitionSpec,
    popTransitionSpec = transitions.popTransitionSpec,
    predictivePopTransitionSpec = transitions.predictivePopTransitionSpec,
)
```
