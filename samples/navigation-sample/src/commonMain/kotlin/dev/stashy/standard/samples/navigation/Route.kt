package dev.stashy.standard.samples.navigation

import androidx.navigation3.runtime.NavKey
import io.ktor.resources.*
import kotlinx.serialization.Serializable

@Serializable
sealed class Route : NavKey {
    @Resource("/")
    @Serializable
    data object Home : Route()

    @Resource("/search")
    @Serializable
    data object Search : Route()

    @Resource("/search/{query}")
    @Serializable
    data class SearchResults(val query: String) : Route()

    @Resource("/profile/{id}")
    @Serializable
    data class Profile(val id: String) : Route()

    @Resource("/article/{slug}")
    @Serializable
    data class Article(val slug: String) : Route()
}
