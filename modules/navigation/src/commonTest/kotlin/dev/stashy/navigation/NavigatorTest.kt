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
private data object Posts : NavKey
private data class PostDetail(val id: Int) : NavKey
private data object Liked : NavKey

class NavigatorTest {
    private val graph = navGraph<NavKey> {
        group {
            entry<Home> { }
            entry<HomeDetail> { }
        }
        group {
            entry<Search> { }
            entry<SearchResult> { }
        }
        group {
            entry<Profile> { }
            group {
                entry<Posts> { }
                entry<PostDetail> { }
            }
            group {
                entry<Liked> { }
            }
        }
    }

    private val homeGroup get() = graph.groupOf(Home)
    private val searchGroup get() = graph.groupOf(Search)
    private val profileGroup get() = graph.groupOf(Profile)
    private val postsGroup get() = graph.groupOf(Posts)
    private val likedGroup get() = graph.groupOf(Liked)

    private fun navigator(): Navigator =
        Navigator(NavigationState(Home, graph), graph)

    @Test
    fun rootEntriesShowStartRouteOnly() {
        val nav = navigator()

        assertEquals(listOf(Home), nav.state.entries.toList())
        assertEquals(Home, nav.state.current)
        assertEquals(homeGroup, nav.state.currentGroup)
        assertEquals(listOf(homeGroup), nav.state.activePath)
    }

    @Test
    fun navigatePushesWithinSameGroup() {
        val nav = navigator()

        nav.navigate(HomeDetail)

        assertEquals(homeGroup, nav.state.currentGroup)
        assertEquals(HomeDetail, nav.state.current)
        assertEquals(listOf(Home, HomeDetail), nav.state.entries.toList())
    }

    @Test
    fun siblingSwitchRetainsOffScreenStack() {
        val nav = navigator()
        nav.navigate(HomeDetail)
        nav.navigate(Search)

        assertEquals(searchGroup, nav.state.currentGroup)
        assertEquals(listOf(Search), nav.state.entries.toList())
        assertEquals(listOf(searchGroup), nav.state.activePath)
        assertEquals(listOf(Home, HomeDetail), nav.state.backStacks.getValue(homeGroup).toList())
    }

    @Test
    fun siblingSwitchAmongThreeRetainsMiddle() {
        val nav = navigator()
        nav.navigate(HomeDetail)
        nav.navigate(Search)
        nav.navigate(SearchResult)
        nav.navigate(Profile)

        assertEquals(listOf(profileGroup), nav.state.activePath)
        assertEquals(listOf(Profile), nav.state.entries.toList())
        assertEquals(listOf(Home, HomeDetail), nav.state.backStacks.getValue(homeGroup).toList())
        assertEquals(listOf(Search, SearchResult), nav.state.backStacks.getValue(searchGroup).toList())
    }

    @Test
    fun navigateToGroupRootOnlySwitchesGroup() {
        val nav = navigator()
        nav.navigate(Search)
        nav.navigate(SearchResult)
        nav.navigate(Home)
        nav.navigate(Search)

        assertEquals(searchGroup, nav.state.currentGroup)
        assertEquals(listOf(Search, SearchResult), nav.state.backStacks.getValue(searchGroup).toList())
        assertEquals(SearchResult, nav.state.current)
        assertEquals(listOf(Search, SearchResult), nav.state.entries.toList())
    }

    @Test
    fun goBackPopsWithinGroup() {
        val nav = navigator()
        nav.navigate(HomeDetail)
        assertTrue(nav.canGoBack())
        nav.goBack()

        assertEquals(listOf(Home), nav.state.entries.toList())
        assertEquals(Home, nav.state.current)
        assertFalse(nav.canGoBack())
    }

    @Test
    fun goBackAtNonStartRootPeerReturnsToStartAndRetains() {
        val nav = navigator()
        nav.navigate(Search)
        assertTrue(nav.canGoBack())
        nav.goBack()

        assertEquals(homeGroup, nav.state.currentGroup)
        assertEquals(listOf(Home), nav.state.entries.toList())
        assertEquals(listOf(Search), nav.state.backStacks.getValue(searchGroup).toList())
        assertFalse(nav.canGoBack())
    }

    @Test
    fun goBackAtStartRootIsNoOp() {
        val nav = navigator()
        assertFalse(nav.canGoBack())
        nav.goBack()

        assertEquals(listOf(Home), nav.state.entries.toList())
    }

    @Test
    fun canGoBackTrueForNestedGroupRoot() {
        val nav = navigator()
        nav.navigate(Profile)
        nav.navigate(Posts)
        assertTrue(nav.canGoBack())
    }

    @Test
    fun nestedNavigateAppendsToActivePath() {
        val nav = navigator()
        nav.navigate(Profile)
        nav.navigate(Posts)

        assertEquals(listOf(profileGroup, postsGroup), nav.state.activePath)
        assertEquals(listOf(Profile, Posts), nav.state.entries.toList())
    }

    @Test
    fun nestedSiblingSwitchRetainsOffScreenNestedStack() {
        val nav = navigator()
        nav.navigate(Profile)
        nav.navigate(Posts)
        nav.navigate(PostDetail(1))
        nav.navigate(Liked)

        assertEquals(listOf(profileGroup, likedGroup), nav.state.activePath)
        assertEquals(listOf(Profile, Liked), nav.state.entries.toList())
        assertEquals(
            listOf(Posts, PostDetail(1)),
            nav.state.backStacks.getValue(postsGroup).toList(),
        )
    }

    @Test
    fun backAtNestedGroupRootClearsNestedStack() {
        val nav = navigator()
        nav.navigate(Profile)
        nav.navigate(Posts)
        nav.navigate(PostDetail(1))
        nav.goBack() // PostDetail -> Posts
        nav.goBack() // clear Posts group, back to Profile only

        assertEquals(listOf(profileGroup), nav.state.activePath)
        assertEquals(listOf(Profile), nav.state.entries.toList())
        assertFalse(postsGroup in nav.state.backStacks)
    }

    @Test
    fun parentSiblingSwitchRetainsNestedStacks() {
        val nav = navigator()
        nav.navigate(Profile)
        nav.navigate(Posts)
        nav.navigate(PostDetail(1))
        nav.navigate(Home)

        assertEquals(listOf(homeGroup), nav.state.activePath)
        assertEquals(listOf(Home), nav.state.entries.toList())
        assertEquals(listOf(Profile), nav.state.backStacks.getValue(profileGroup).toList())
        assertEquals(
            listOf(Posts, PostDetail(1)),
            nav.state.backStacks.getValue(postsGroup).toList(),
        )

        nav.navigate(Profile)
        assertEquals(listOf(profileGroup), nav.state.activePath)
        assertEquals(listOf(Profile), nav.state.entries.toList())
        assertEquals(
            listOf(Posts, PostDetail(1)),
            nav.state.backStacks.getValue(postsGroup).toList(),
        )

        nav.navigate(Posts)
        assertEquals(listOf(profileGroup, postsGroup), nav.state.activePath)
        assertEquals(listOf(Profile, Posts, PostDetail(1)), nav.state.entries.toList())
    }

    @Test
    fun popToReturnsNullWhenRouteNotOnStack() {
        val nav = navigator()

        assertEquals(null, nav.popTo(HomeDetail))
        assertEquals(listOf(Home), nav.state.entries.toList())
    }

    @Test
    fun popToOrNavigatePushesWhenMissing() {
        val nav = navigator()

        nav.popTo(HomeDetail) ?: nav.navigate(HomeDetail)

        assertEquals(homeGroup, nav.state.currentGroup)
        assertEquals(listOf(Home, HomeDetail), nav.state.entries.toList())
    }

    @Test
    fun popToPopsToExistingEntry() {
        val nav = navigator()
        nav.navigate(HomeDetail)
        nav.navigate(Search)
        assertEquals(Unit, nav.popTo(HomeDetail))

        assertEquals(listOf(Home, HomeDetail), nav.state.entries.toList())
        assertEquals(listOf(Search), nav.state.backStacks.getValue(searchGroup).toList())
    }

    @Test
    fun popToStartRootTruncatesStartStack() {
        val nav = navigator()
        nav.navigate(HomeDetail)
        assertEquals(Unit, nav.popTo(Home))

        assertEquals(homeGroup, nav.state.currentGroup)
        assertEquals(listOf(Home), nav.state.entries.toList())
    }

    @Test
    fun popToStartFromNestedRetainsOtherGroups() {
        val nav = navigator()
        nav.navigate(Profile)
        nav.navigate(Posts)
        nav.navigate(PostDetail(1))
        assertEquals(Unit, nav.popTo(Home))

        assertEquals(listOf(homeGroup), nav.state.activePath)
        assertEquals(listOf(Home), nav.state.entries.toList())
        assertEquals(listOf(Profile), nav.state.backStacks.getValue(profileGroup).toList())
        assertEquals(
            listOf(Posts, PostDetail(1)),
            nav.state.backStacks.getValue(postsGroup).toList(),
        )
    }

    @Test
    fun entriesIsStableWhenStateUnchanged() {
        val nav = navigator()
        nav.navigate(HomeDetail)

        val first = nav.state.entries
        val second = nav.state.entries

        assertSame(first, second)
    }

    @Test
    fun entriesReflectMutationsWithStableInstance() {
        val nav = navigator()
        nav.navigate(HomeDetail)
        val view = nav.state.entries

        Snapshot.withMutableSnapshot {
            nav.navigate(Search)
        }

        assertSame(view, nav.state.entries)
        assertEquals(listOf(Search), nav.state.entries.toList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun entriesViewTracksSnapshotChanges() = runTest {
        val nav = navigator()
        val emissions = mutableListOf<List<NavKey>>()

        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                snapshotFlow { nav.state.entries.toList() }
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
        assertEquals(listOf(Search), emissions.last())
    }
}
