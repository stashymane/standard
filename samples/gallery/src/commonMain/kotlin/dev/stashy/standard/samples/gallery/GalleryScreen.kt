package dev.stashy.standard.samples.gallery

import androidx.compose.runtime.Composable

/**
 * A standalone component sample.
 *
 * Implementations are registered with Koin's [org.koin.core.annotation.Factory] annotation and are
 * discovered by [GalleryModule]'s component scan.
 */
internal interface GalleryScreen {
    /** Stable identifier used in the route and as the list key. */
    val id: String

    /** Display name shown in the gallery and the top bar. */
    val title: String

    /** Short explanation shown in the gallery list. */
    val description: String

    /** The sample content. */
    @Composable
    fun Content()
}
