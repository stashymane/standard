# navigation-ui

UI utilities for Navigation3, including scene transition specs.

## Transitions

```kotlin
val order = listOf(Projects.group, Media.group, About.group)
val transitions = NavTransitions(
    groupOrder = { from, to -> order.indexOf(from).compareTo(order.indexOf(to)) },
)

NavDisplay(
    entries = state.toDecoratedEntries(),
    onBack = navigator::goBack,
    transitionSpec = transitions.transitionSpec,
    popTransitionSpec = transitions.popTransitionSpec,
    predictivePopTransitionSpec = transitions.predictivePopTransitionSpec,
)
```

- Same-group push/pop: scale+fade
- Predictive back: fade + scale-out, sliding with the swipe edge
- Group switch (when `groupOrder` is non-zero): subtle horizontal slide

`toDecoratedEntries` attaches each destination’s `GroupId` as metadata so
transitions can detect group changes without app-specific keys.
