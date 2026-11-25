package dev.stashy.standard.samples.gallery

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(
        configure = {
            isClearFocusOnMouseDownEnabled = true
        },
    ) {
        App()
    }
}
