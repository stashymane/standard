# ui-gradients

Better gradients for Compose, with Oklab interpolation via [colormath](https://github.com/ajalt/colormath).

Requires `com.github.ajalt.colormath`.

## Usage

```kotlin
Brush.easedLinearGradient {
    stop(Color.Red)
    stop(Color.Blue, 0.6f)
}
```

```kotlin
val colors = easeColors {
    stop(Color.Red)
    stop(Color.Green)
    stop(Color.Blue)
}
```
