package dev.stashy.standard.samples.navigation

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Navigation Sample",
    ) {
        App()
    }
}
