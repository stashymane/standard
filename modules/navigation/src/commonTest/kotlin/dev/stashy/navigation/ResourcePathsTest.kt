package dev.stashy.navigation

import dev.stashy.navigation.resources.fromResourcePath
import dev.stashy.navigation.resources.normalizeResourceLocation
import dev.stashy.navigation.resources.splitResourceLocation
import dev.stashy.navigation.resources.toResourcePath
import io.ktor.resources.*
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

@OptIn(InternalNavApi::class)
class ResourcePathsTest {
    private val testRoutesSerializer = TestRoutes.serializer()
    private val nestedByIdSerializer = NestedUsers.ById.serializer()

    @Test
    fun homeRoundTrip() {
        val route = TestRoutes.Home
        assertEquals("/home", route.toResourcePath(testRoutesSerializer))
        assertEquals(TestRoutes.Home, fromResourcePath("/home", testRoutesSerializer))
    }

    @Test
    fun queryParameterRoundTrip() {
        val route = TestRoutes.Child("value")
        assertEquals("/parent/child?param1=value", route.toResourcePath(testRoutesSerializer))
        assertEquals(route, fromResourcePath("/parent/child?param1=value", testRoutesSerializer))
    }

    @Test
    fun listQueryRoundTrip() {
        val route = TestRoutes.Items(listOf("a", "b"))
        val location = route.toResourcePath(testRoutesSerializer)
        assertEquals(route, fromResourcePath(location, testRoutesSerializer))
    }

    @Test
    fun defaultQueryRestoredOnDecodeWhenOmittedFromUrl() {
        assertEquals(TestRoutes.Articles(), fromResourcePath("/articles", testRoutesSerializer))
        val encoded = TestRoutes.Articles().toResourcePath(testRoutesSerializer)
        assertEquals(TestRoutes.Articles(), fromResourcePath(encoded, testRoutesSerializer))
    }

    @Test
    fun tailPathRoundTrip() {
        val route = TestRoutes.Docs(listOf("guide", "intro"))
        assertEquals("/docs/guide/intro", route.toResourcePath(testRoutesSerializer))
        assertEquals(route, fromResourcePath("/docs/guide/intro", testRoutesSerializer))
    }

    @Test
    fun optionalSegmentWithValue() {
        val route = TestRoutes.OptionalSegment("x")
        assertEquals("/optional/x", route.toResourcePath(testRoutesSerializer))
        assertEquals(route, fromResourcePath("/optional/x", testRoutesSerializer))
    }

    @Test
    fun optionalSegmentWithoutValue() {
        assertEquals("/optional", TestRoutes.OptionalSegment().toResourcePath(testRoutesSerializer))
        assertEquals(
            TestRoutes.OptionalSegment(),
            fromResourcePath("/optional", testRoutesSerializer),
        )
    }

    @Test
    fun sealedDisambiguationByStaticSegment() {
        assertEquals(TestRoutes.UsersList, fromResourcePath("/users", testRoutesSerializer))
        assertEquals(
            TestRoutes.UserDetail(42),
            fromResourcePath("/users/42", testRoutesSerializer),
        )
    }

    @Test
    fun nestedResourceRoundTrip() {
        val route = NestedUsers.ById(id = 7)
        assertEquals("/users/7", route.toResourcePath(nestedByIdSerializer))
        assertEquals(7, fromResourcePath("/users/7", nestedByIdSerializer)?.id)
    }

    @Test
    fun normalizeResourceLocationStableQuery() {
        val encoded = TestRoutes.Child("value").toResourcePath(testRoutesSerializer)
        assertEquals(encoded, normalizeResourceLocation(encoded))
    }

    @Test
    fun unknownLocationReturnsNull() {
        assertNull(fromResourcePath("/does-not-exist", testRoutesSerializer))
    }

    @Test
    fun splitResourceLocationStripsFragment() {
        val (path, query) = splitResourceLocation("/a/b?c=1#section")
        assertEquals("/a/b", path)
        assertEquals("c=1", query)
    }
}
