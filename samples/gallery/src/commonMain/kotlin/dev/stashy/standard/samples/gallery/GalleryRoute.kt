package dev.stashy.standard.samples.gallery

import androidx.navigation3.runtime.NavKey
import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
internal sealed class GalleryRoute : NavKey {
    @Serializable
    @Resource("/")
    data object Home : GalleryRoute()

    @Serializable
    @Resource("/samples/{id}")
    data class Sample(val id: String) : GalleryRoute()
}
