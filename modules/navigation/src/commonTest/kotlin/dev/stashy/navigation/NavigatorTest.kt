package dev.stashy.navigation

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

private data object Home : NavKey
private data object HomeDetail : NavKey
private data object Search : NavKey
private data object SearchResult : NavKey
private data object Profile : NavKey
private data class PostDetail(val id: Int) : NavKey

class NavigatorTest {
    private val topLevelRoutes = setOf(Home, Search, Profile)

    private fun navigator(): Navigator =
        Navigator(NavigationState(Home, topLevelRoutes))

    @Test
    fun rootBackStackShowsStartRouteOnly() {
        val nav = navigator()

        assertEquals(listOf(Home), nav.state.backStack.toList())
        assertEquals(Home, nav.state.current)
        assertEquals(Home, nav.state.topLevelRoute)
        assertFalse(nav.canGoBack())
    }

    @Test
    fun navigatePushesOntoCurrentStack() {
        val nav = navigator()

        nav.navigate(HomeDetail)

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(HomeDetail, nav.state.current)
        assertEquals(listOf(Home, HomeDetail), nav.state.backStack.toList())
    }

    @Test
    fun navigateToTopLevelSwitchesStackAndKeepsStartVisible() {
        val nav = navigator()
        nav.navigate(HomeDetail)
        nav.navigate(Search)

        assertEquals(Search, nav.state.topLevelRoute)
        assertEquals(Search, nav.state.current)
        assertEquals(listOf(Home, Search), nav.state.backStack.toList())
        assertEquals(listOf(Home, HomeDetail), nav.state.backStacks.getValue(Home).toList())
    }

    @Test
    fun switchBackRestoresRetainedStack() {
        val nav = navigator()
        nav.navigate(HomeDetail)
        nav.navigate(Search)
        nav.navigate(Home)

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home, HomeDetail), nav.state.backStack.toList())
        assertEquals(listOf(Search), nav.state.backStacks.getValue(Search).toList())
    }

    @Test
    fun navigateToTopLevelRootDoesNotPush() {
        val nav = navigator()
        nav.navigate(Search)
        nav.navigate(Search)

        assertEquals(listOf(Search), nav.state.backStacks.getValue(Search).toList())
        assertEquals(listOf(Home, Search), nav.state.backStack.toList())
    }

    @Test
    fun goBackPopsWithinStack() {
        val nav = navigator()
        nav.navigate(HomeDetail)

        assertTrue(nav.canGoBack())
        nav.goBack()

        assertEquals(listOf(Home), nav.state.backStack.toList())
        assertEquals(Home, nav.state.current)
        assertFalse(nav.canGoBack())
    }

    @Test
    fun goBackAtNonStartRootReturnsToStartAndRetains() {
        val nav = navigator()
        nav.navigate(Search)

        assertTrue(nav.canGoBack())
        nav.goBack()

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home), nav.state.backStack.toList())
        assertEquals(listOf(Search), nav.state.backStacks.getValue(Search).toList())
        assertFalse(nav.canGoBack())
    }

    @Test
    fun goBackAtStartRootIsNoOp() {
        val nav = navigator()

        assertFalse(nav.canGoBack())
        nav.goBack()

        assertEquals(listOf(Home), nav.state.backStack.toList())
    }

    @Test
    fun canGoBackTracksState() {
        val nav = navigator()

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
    fun popToReturnsNullWhenRouteNotOnStack() {
        val nav = navigator()

        assertEquals(null, nav.popTo(SearchResult))
        assertEquals(listOf(Home), nav.state.backStack.toList())
    }

    @Test
    fun popToOrNavigatePushesWhenMissing() {
        val nav = navigator()

        nav.popTo(HomeDetail) ?: nav.navigate(HomeDetail)

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home, HomeDetail), nav.state.backStack.toList())
    }

    @Test
    fun popToPopsToExistingEntry() {
        val nav = navigator()
        nav.navigate(HomeDetail)
        nav.navigate(Search)

        assertEquals(Unit, nav.popTo(HomeDetail))

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home, HomeDetail), nav.state.backStack.toList())
        assertEquals(listOf(Search), nav.state.backStacks.getValue(Search).toList())
    }

    @Test
    fun popToStartRootTruncatesStartStack() {
        val nav = navigator()
        nav.navigate(HomeDetail)

        assertEquals(Unit, nav.popTo(Home))

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home), nav.state.backStack.toList())
    }

    @Test
    fun popToOtherTopLevelSwitches() {
        val nav = navigator()
        nav.navigate(Search)

        assertEquals(Unit, nav.popTo(Home))

        assertEquals(Home, nav.state.topLevelRoute)
        assertEquals(listOf(Home), nav.state.backStack.toList())
        assertEquals(listOf(Search), nav.state.backStacks.getValue(Search).toList())
    }

    @Test
    fun popToEntryInOffScreenStack() {
        val nav = navigator()
        nav.navigate(Search)
        nav.navigate(SearchResult)
        nav.navigate(Home)

        assertEquals(Unit, nav.popTo(SearchResult))

        assertEquals(Search, nav.state.topLevelRoute)
        assertEquals(listOf(Home, Search, SearchResult), nav.state.backStack.toList())
        assertEquals(listOf(Home), nav.state.backStacks.getValue(Home).toList())
    }

    @Test
    fun backStackIsStableWhenStateUnchanged() {
        val nav = navigator()
        nav.navigate(HomeDetail)

        val first = nav.state.backStack
        val second = nav.state.backStack

        assertSame(first, second)
    }

    @Test
    fun backStackReflectsMutationsWithStableInstance() {
        val nav = navigator()
        nav.navigate(HomeDetail)
        val view = nav.state.backStack

        Snapshot.withMutableSnapshot {
            nav.navigate(Search)
        }

        assertSame(view, nav.state.backStack)
        assertEquals(listOf(Home, Search), nav.state.backStack.toList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun backStackViewTracksSnapshotChanges() = runTest {
        val nav = navigator()
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
        assertEquals(listOf(Home, Search), emissions.last())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun reselectEventsEmittedOnReselect() = runTest {
        val nav = navigator()
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
