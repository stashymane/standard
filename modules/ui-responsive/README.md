# ui-adaptive

Responsive Compose utilities with axis-aware breakpoint comparisons.
Alternative to `material3-adaptive`'s `currentWindowAdaptiveInfoV2`/`WindowSizeClass`.

```kotlin
val adaptiveInfo = rememberAdaptiveInfo()
CompositionLocalProvider(LocalAdaptiveInfo provides adaptiveInfo) {
    // content
}

val adaptiveInfo = LocalAdaptiveInfo.current
val showTopBar = adaptiveInfo.height >= ContainerSize.Medium
val collapseSidebar = adaptiveInfo.width < ContainerSize.Medium
val roomyLayout = adaptiveInfo >= ContainerSize.Medium // width and height
```
