package dev.stashy.navigation

import kotlin.test.*

class NavigatorStackSwitchingTest {
    @Test
    fun `current contains the selected top-level stack`() {
        val nav = testNavigator()

        nav.navigate(HomeDetail)
        assertEquals(listOf(Home, HomeDetail), nav.state.current)

        nav.navigate(Search)
        nav.navigate(SearchResult)
        assertEquals(listOf(Search, SearchResult), nav.state.current)

        nav.navigate(Home)
        assertEquals(listOf(Home, HomeDetail), nav.state.current)
    }

    @Test
    fun `switching top-level keeps the start visible`() {
        val nav = testNavigator()
        nav.navigate(HomeDetail)
        nav.navigate(Search)

        assertEquals(Search, nav.state.topLevelRoute)
        assertEquals(listOf(Search), nav.state.current)
        assertEquals(listOf(Home, HomeDetail, Search), nav.state.backStack.toList())
        assertEquals(listOf(Home, HomeDetail), nav.state.backStacks.getValue(Home).toList())
    }

    @Test
    fun `switching back restores the retained stack`() {
        val nav = testNavigator()
        nav.navigate(HomeDetail)
        nav.navigate(Search)
        nav.navigate(Home)

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home, HomeDetail), nav.state.backStack.toList())
        assertEquals(listOf(Search), nav.state.backStacks.getValue(Search).toList())
    }

    @Test
    fun `navigating to the current top-level root does not push`() {
        val nav = testNavigator()
        nav.navigate(Search)
        nav.navigate(Search)

        assertEquals(listOf(Search), nav.state.backStacks.getValue(Search).toList())
        assertEquals(listOf(Home, Search), nav.state.backStack.toList())
    }

    @Test
    fun `going back at a non-start root returns to start`() {
        val nav = testNavigator()
        nav.navigate(Search)

        assertTrue(nav.canGoBack())
        nav.goBack()

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home), nav.state.backStack.toList())
        assertEquals(listOf(Search), nav.state.backStacks.getValue(Search).toList())
        assertFalse(nav.canGoBack())
    }

    @Test
    fun `pop to finds an existing entry`() {
        val nav = testNavigator()
        nav.navigate(HomeDetail)
        nav.navigate(Search)

        assertEquals(Unit, nav.popTo(HomeDetail))

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home, HomeDetail), nav.state.backStack.toList())
        assertEquals(listOf(Search), nav.state.backStacks.getValue(Search).toList())
    }

    @Test
    fun `pop to another top-level switches stacks`() {
        val nav = testNavigator()
        nav.navigate(Search)

        assertEquals(Unit, nav.popTo(Home))

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home), nav.state.backStack.toList())
        assertEquals(listOf(Search), nav.state.backStacks.getValue(Search).toList())
    }

    @Test
    fun `pop to finds an off-screen entry`() {
        val nav = testNavigator()
        nav.navigate(Search)
        nav.navigate(SearchResult)
        nav.navigate(Home)

        assertEquals(Unit, nav.popTo(SearchResult))

        assertEquals(Search, nav.state.topLevelRoute)
        assertEquals(listOf(Home, Search, SearchResult), nav.state.backStack.toList())
        assertEquals(listOf(Home), nav.state.backStacks.getValue(Home).toList())
    }
}
