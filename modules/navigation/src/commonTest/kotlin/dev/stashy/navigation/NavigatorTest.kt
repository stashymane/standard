package dev.stashy.navigation

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NavigatorTest {
    @Test
    fun `root stack contains only the start route`() {
        val nav = testNavigator()

        assertEquals(listOf(Home), nav.state.backStack.toList())
        assertEquals(listOf(Home), nav.state.current)
        assertEquals(Home, nav.state.topLevelRoute)
        assertFalse(nav.canGoBack())
    }

    @Test
    fun `navigate pushes onto the current stack`() {
        val nav = testNavigator()

        nav.navigate(HomeDetail)

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home, HomeDetail), nav.state.current)
        assertEquals(listOf(Home, HomeDetail), nav.state.backStack.toList())
    }

    @Test
    fun `go back pops the current stack`() {
        val nav = testNavigator()
        nav.navigate(HomeDetail)

        assertTrue(nav.canGoBack())
        nav.goBack()

        assertEquals(listOf(Home), nav.state.backStack.toList())
        assertEquals(listOf(Home), nav.state.current)
        assertFalse(nav.canGoBack())
    }

    @Test
    fun `go back at the start root is a no-op`() {
        val nav = testNavigator()

        assertFalse(nav.canGoBack())
        nav.goBack()

        assertEquals(listOf(Home), nav.state.backStack.toList())
    }

    @Test
    fun `can go back tracks navigation state`() {
        val nav = testNavigator()

        assertFalse(nav.canGoBack())
        nav.navigate(HomeDetail)
        assertTrue(nav.canGoBack())
        nav.goBack()
        nav.navigate(Search)
        assertTrue(nav.canGoBack())
        nav.goBack()
        assertFalse(nav.canGoBack())
    }

    @Test
    fun `pop to returns null when the route is missing`() {
        val nav = testNavigator()

        assertEquals(null, nav.popTo(SearchResult))
        assertEquals(listOf(Home), nav.state.backStack.toList())
    }

    @Test
    fun `pop to or navigate pushes a missing route`() {
        val nav = testNavigator()

        nav.popTo(HomeDetail) ?: nav.navigate(HomeDetail)

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home, HomeDetail), nav.state.backStack.toList())
    }

    @Test
    fun `pop to the start root keeps the stack`() {
        val nav = testNavigator()
        nav.navigate(HomeDetail)

        assertEquals(Unit, nav.popTo(Home))

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home, HomeDetail), nav.state.backStack.toList())
    }

    @Test
    fun `back stack is stable while state is unchanged`() {
        val nav = testNavigator()
        nav.navigate(HomeDetail)

        val first = nav.state.backStack
        val second = nav.state.backStack

        assertSame(first, second)
    }

    @Test
    fun `back stack view reflects mutations`() {
        val nav = testNavigator()
        nav.navigate(HomeDetail)
        val view = nav.state.backStack

        Snapshot.withMutableSnapshot {
            nav.navigate(Search)
        }

        assertSame(view, nav.state.backStack)
        assertEquals(listOf(Home, HomeDetail, Search), nav.state.backStack.toList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `back stack view tracks snapshot changes`() = runTest {
        val nav = testNavigator()
        val emissions = mutableListOf<List<NavKey>>()

        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                snapshotFlow { nav.state.backStack.toList() }
                    .collect { emissions.add(it) }
            }

        Snapshot.withMutableSnapshot {
            nav.navigate(HomeDetail)
        }
        Snapshot.withMutableSnapshot {
            nav.navigate(Search)
        }

        job.cancel()

        assertTrue(emissions.any { it == listOf(Home, HomeDetail) })
        assertEquals(listOf(Home, HomeDetail, Search), emissions.last())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `reselect emits the reselected route`() = runTest {
        val nav = testNavigator()
        val emissions = mutableListOf<NavKey>()

        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                nav.reselectEvents.collect { emissions.add(it) }
            }

        nav.onReselect(Home)
        nav.onReselect(Search)

        job.cancel()

        assertEquals(listOf(Home, Search), emissions)
    }
}
