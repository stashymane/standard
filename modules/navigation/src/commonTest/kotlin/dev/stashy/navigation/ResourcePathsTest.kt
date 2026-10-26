package dev.stashy.navigation

import androidx.navigation3.runtime.NavKey
import dev.stashy.navigation.resources.*
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

@Serializable
sealed interface OverlayTestRoutes : NavKey {
    @Resource("/overlay")
    data object OverlayOnly : OverlayTestRoutes, OverlayNavKey
}

@Serializable
sealed interface CollidingTestRoutes : NavKey {
    @Resource("/colliding")
    data object OverlayCandidate : CollidingTestRoutes, OverlayNavKey

    @Resource("/colliding")
    data object AddressableCandidate : CollidingTestRoutes
}

class ResourcePathsTest {
    private val testRoutesSerializer = TestRoutes.serializer()
    private val nestedByIdSerializer = NestedUsers.ById.serializer()

    @Test
    fun `home route round-trips through its path`() {
        val route = TestRoutes.Home
        assertEquals("/home", route.toResourcePath(testRoutesSerializer))
        assertEquals(TestRoutes.Home, fromResourcePath("/home", testRoutesSerializer))
    }

    @Test
    fun `query parameters round-trip`() {
        val route = TestRoutes.Child("value")
        assertEquals("/parent/child?param1=value", route.toResourcePath(testRoutesSerializer))
        assertEquals(route, fromResourcePath("/parent/child?param1=value", testRoutesSerializer))
    }

    @Test
    fun `list query parameters round-trip`() {
        val route = TestRoutes.Items(listOf("a", "b"))
        val location = route.toResourcePath(testRoutesSerializer)
        assertEquals(route, fromResourcePath(location, testRoutesSerializer))
    }

    @Test
    fun `default query values restore when omitted`() {
        assertEquals(TestRoutes.Articles(), fromResourcePath("/articles", testRoutesSerializer))
        val encoded = TestRoutes.Articles().toResourcePath(testRoutesSerializer)
        assertEquals(TestRoutes.Articles(), fromResourcePath(encoded, testRoutesSerializer))
    }

    @Test
    fun `tail path segments round-trip`() {
        val route = TestRoutes.Docs(listOf("guide", "intro"))
        assertEquals("/docs/guide/intro", route.toResourcePath(testRoutesSerializer))
        assertEquals(route, fromResourcePath("/docs/guide/intro", testRoutesSerializer))
    }

    @Test
    fun `optional segment round-trips when present`() {
        val route = TestRoutes.OptionalSegment("x")
        assertEquals("/optional/x", route.toResourcePath(testRoutesSerializer))
        assertEquals(route, fromResourcePath("/optional/x", testRoutesSerializer))
    }

    @Test
    fun `optional segment round-trips when absent`() {
        assertEquals("/optional", TestRoutes.OptionalSegment().toResourcePath(testRoutesSerializer))
        assertEquals(
            TestRoutes.OptionalSegment(),
            fromResourcePath("/optional", testRoutesSerializer),
        )
    }

    @Test
    fun `sealed routes disambiguate by static segments`() {
        assertEquals(TestRoutes.UsersList, fromResourcePath("/users", testRoutesSerializer))
        assertEquals(
            TestRoutes.UserDetail(42),
            fromResourcePath("/users/42", testRoutesSerializer),
        )
    }

    @Test
    fun `nested resources round-trip`() {
        val route = NestedUsers.ById(id = 7)
        assertEquals("/users/7", route.toResourcePath(nestedByIdSerializer))
        assertEquals(7, fromResourcePath("/users/7", nestedByIdSerializer)?.id)
    }

    @Test
    fun `normalization keeps query encoding stable`() {
        val encoded = TestRoutes.Child("value").toResourcePath(testRoutesSerializer)
        assertEquals(encoded, normalizeResourceLocation(encoded))
    }

    @Test
    fun `unknown locations return null`() {
        assertNull(fromResourcePath("/does-not-exist", testRoutesSerializer))
    }

    @Test
    fun `splitting strips the fragment`() {
        val (path, query) = splitResourceLocation("/a/b?c=1#section")
        assertEquals("/a/b", path)
        assertEquals("c=1", query)
    }

    @Test
    fun `decoding rejects overlays`() {
        assertNull(
            decodeFromResourcePath(
                location = "/overlay",
                rootSerializer = OverlayTestRoutes.serializer(),
                resourcesFormat = defaultResourcesFormat,
                accept = { it !is OverlayNavKey },
            )
        )
    }

    @Test
    fun `decoding skips rejected overlays`() {
        assertEquals(
            CollidingTestRoutes.AddressableCandidate,
            decodeFromResourcePath(
                location = "/colliding",
                rootSerializer = CollidingTestRoutes.serializer(),
                resourcesFormat = defaultResourcesFormat,
                accept = { it !is OverlayNavKey },
            ),
        )
    }
}
