package dev.stashy.navigation

import dev.stashy.navigation.state.NavigationState
import kotlin.test.*

class NavigatorOverlayTest {
    @Test
    fun `navigate pushes the overlay onto the current stack`() {
        val nav = testNavigator()

        nav.navigate(Share(1))

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home, Share(1)), nav.state.current)
        assertEquals(listOf(Home, Share(1)), nav.state.backStack.toList())
        assertTrue(nav.canGoBack())
    }

    @Test
    fun `go back pops the overlay first`() {
        val nav = testNavigator()
        nav.navigate(HomeDetail)
        nav.navigate(Share(1))

        nav.goBack()

        assertEquals(listOf(Home, HomeDetail), nav.state.current)
        assertEquals(listOf(Home, HomeDetail), nav.state.backStack.toList())
        assertTrue(nav.canGoBack())
    }

    @Test
    fun `stacked overlays pop one at a time`() {
        val nav = testNavigator()
        nav.navigate(Share(1))
        nav.navigate(Share(2))

        assertEquals(listOf(Home, Share(1), Share(2)), nav.state.current)

        nav.goBack()
        assertEquals(listOf(Home, Share(1)), nav.state.current)

        nav.goBack()
        assertEquals(listOf(Home), nav.state.current)
        assertFalse(nav.canGoBack())
    }

    @Test
    fun `navigate dismisses the overlay`() {
        val nav = testNavigator()
        nav.navigate(Share(1))
        nav.navigate(HomeDetail)

        assertEquals(listOf(Home, HomeDetail), nav.state.current)
    }

    @Test
    fun `top-level navigation dismisses the overlay`() {
        val nav = testNavigator()
        nav.navigate(HomeDetail)
        nav.navigate(Share(1))
        nav.navigate(Search)

        assertEquals(Search, nav.state.topLevelRoute)
        assertEquals(listOf(Search), nav.state.current)
        assertEquals(listOf(Home, HomeDetail), nav.state.backStacks.getValue(Home).toList())
    }

    @Test
    fun `navigating to the current top-level dismisses the overlay`() {
        val nav = testNavigator()
        nav.navigate(HomeDetail)
        nav.navigate(Share(1))
        nav.navigate(Home)

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home, HomeDetail), nav.state.current)
    }

    @Test
    fun `switching top-level discards the overlay`() {
        val nav = testNavigator()
        nav.navigate(Search)
        nav.navigate(Share(1))
        nav.navigate(Home)
        nav.navigate(Search)

        assertEquals(listOf(Search), nav.state.current)
    }

    @Test
    fun `switching from a start-route overlay discards it`() {
        val nav = testNavigator()
        nav.navigate(Share(1))
        nav.navigate(Search)
        nav.navigate(Home)

        assertEquals(listOf(Home), nav.state.current)
        assertFalse(nav.canGoBack())
    }

    @Test
    fun `pop to an overlay pops deeper overlays`() {
        val nav = testNavigator()
        nav.navigate(HomeDetail)
        nav.navigate(Share(1))
        nav.navigate(Share(2))

        assertEquals(Unit, nav.popTo(Share(1)))

        assertEquals(listOf(Home, HomeDetail, Share(1)), nav.state.current)
    }

    @Test
    fun `pop to a non-overlay dismisses the overlay`() {
        val nav = testNavigator()
        nav.navigate(HomeDetail)
        nav.navigate(Share(1))

        assertEquals(Unit, nav.popTo(HomeDetail))

        assertEquals(listOf(Home, HomeDetail), nav.state.current)
    }

    @Test
    fun `pop to a missing overlay returns null`() {
        val nav = testNavigator()

        assertEquals(null, nav.popTo(Share(1)))
        assertEquals(listOf(Home), nav.state.current)
    }

    @Test
    fun `last non-overlay route skips overlays`() {
        val nav = testNavigator()
        assertEquals(Home, nav.state.lastNonOverlayRoute())

        nav.navigate(HomeDetail)
        assertEquals(HomeDetail, nav.state.lastNonOverlayRoute())

        nav.navigate(Confirm)
        assertEquals(HomeDetail, nav.state.lastNonOverlayRoute())

        nav.navigate(Search)
        assertEquals(Search, nav.state.lastNonOverlayRoute())
    }

    @Test
    fun `start route cannot be an overlay`() {
        assertFailsWith<IllegalArgumentException> {
            NavigationState(Share(1), setOf(Share(1)))
        }
    }

    @Test
    fun `top-level routes cannot contain overlays`() {
        assertFailsWith<IllegalArgumentException> {
            NavigationState(Home, setOf(Home, Confirm))
        }
    }
}
