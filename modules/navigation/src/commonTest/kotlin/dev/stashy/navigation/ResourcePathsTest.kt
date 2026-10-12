package dev.stashy.navigation

import io.ktor.resources.Resource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.Serializable

@Serializable
sealed interface TestRoutes {
    @Resource("/home")
    data object Home : TestRoutes

    @Resource("/parent/child")
    data class Child(val param1: String? = null) : TestRoutes

    @Resource("/items")
    data class Items(val tags: List<String> = emptyList()) : TestRoutes

    @Resource("/articles")
    data class Articles(val sort: String? = "new") : TestRoutes

    @Resource("/docs/{path...}")
    data class Docs(val path: List<String>) : TestRoutes

    @Resource("/optional/{segment?}")
    data class OptionalSegment(val segment: String? = null) : TestRoutes

    @Resource("/users")
    data object UsersList : TestRoutes

    @Resource("/users/{id}")
    data class UserDetail(val id: Long) : TestRoutes
}

@Serializable
@Resource("/users")
class NestedUsers {
    @Serializable
    @Resource("/{id}")
    data class ById(val parent: NestedUsers = NestedUsers(), val id: Long)
}

class ResourcePathsTest {

    @Test
    fun homeRoundTrip() {
        val route = TestRoutes.Home
        assertEquals("/home", route.toResourcePath())
        assertEquals(TestRoutes.Home, fromResourcePath<TestRoutes>("/home"))
    }

    @Test
    fun queryParameterRoundTrip() {
        val route = TestRoutes.Child("value")
        assertEquals("/parent/child?param1=value", route.toResourcePath())
        assertEquals(route, fromResourcePath("/parent/child?param1=value"))
    }

    @Test
    fun listQueryRoundTrip() {
        val route = TestRoutes.Items(listOf("a", "b"))
        val location = route.toResourcePath()
        assertEquals(route, fromResourcePath<TestRoutes>(location))
    }

    @Test
    fun defaultQueryRestoredOnDecodeWhenOmittedFromUrl() {
        assertEquals(TestRoutes.Articles(), fromResourcePath<TestRoutes>("/articles"))
        val encoded = TestRoutes.Articles().toResourcePath()
        assertEquals(TestRoutes.Articles(), fromResourcePath<TestRoutes>(encoded))
    }

    @Test
    fun tailPathRoundTrip() {
        val route = TestRoutes.Docs(listOf("guide", "intro"))
        assertEquals("/docs/guide/intro", route.toResourcePath())
        assertEquals(route, fromResourcePath<TestRoutes>("/docs/guide/intro"))
    }

    @Test
    fun optionalSegmentWithValue() {
        val route = TestRoutes.OptionalSegment("x")
        assertEquals("/optional/x", route.toResourcePath())
        assertEquals(route, fromResourcePath<TestRoutes>("/optional/x"))
    }

    @Test
    fun optionalSegmentWithoutValue() {
        assertEquals("/optional", TestRoutes.OptionalSegment().toResourcePath())
        assertEquals(TestRoutes.OptionalSegment(), fromResourcePath<TestRoutes>("/optional"))
    }

    @Test
    fun sealedDisambiguationByStaticSegment() {
        assertEquals(TestRoutes.UsersList, fromResourcePath<TestRoutes>("/users"))
        assertEquals(TestRoutes.UserDetail(42), fromResourcePath<TestRoutes>("/users/42"))
    }

    @Test
    fun nestedResourceRoundTrip() {
        val route = NestedUsers.ById(id = 7)
        assertEquals("/users/7", route.toResourcePath())
        assertEquals(7, fromResourcePath<NestedUsers.ById>("/users/7")?.id)
    }

    @Test
    fun normalizeResourceLocationStableQuery() {
        val encoded = TestRoutes.Child("value").toResourcePath()
        assertEquals(encoded, normalizeResourceLocation(encoded))
    }

    @Test
    fun unknownLocationReturnsNull() {
        assertNull(fromResourcePath<TestRoutes>("/does-not-exist"))
    }

    @Test
    fun splitResourceLocationStripsFragment() {
        val (path, query) = splitResourceLocation("/a/b?c=1#section")
        assertEquals("/a/b", path)
        assertEquals("c=1", query)
    }
}
