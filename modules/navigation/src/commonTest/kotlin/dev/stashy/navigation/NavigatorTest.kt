package dev.stashy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

private val GroupA = GroupId("a")
private val GroupB = GroupId("b")

private data class TestDestination(
    val name: String,
    override val group: GroupId = GroupId.Default,
) : NavDestination {
    @Composable
    override fun Content() {
    }
}

class NavigatorTest {
    private val home = TestDestination("home", GroupA)
    private val pageA1 = TestDestination("page-a1", GroupA)
    private val pageA2 = TestDestination("page-a2", GroupA)
    private val pageB1 = TestDestination("page-b1", GroupB)
    private val pageBRoot = TestDestination("b-root", GroupB)
    private val defaultHome = TestDestination("default-home")

    private fun navigator(): Navigator = Navigator(NavigationState(home))

    @Test
    fun defaultGroupUsedWhenDestinationOmitsGroup() {
        val nav = Navigator(NavigationState(defaultHome))

        assertEquals(GroupId.Default, nav.state.currentGroup)
        assertEquals(GroupId.Default, nav.state.startGroup)
        assertEquals(listOf(defaultHome), nav.state.entries.toList())
        assertEquals(listOf(GroupId.Default), nav.state.stacksInUse)
    }

    @Test
    fun rootEntriesShowStartRouteOnly() {
        val nav = navigator()

        assertEquals(listOf(home), nav.state.entries.toList())
        assertEquals(home, nav.state.current)
        assertEquals(GroupA, nav.state.currentGroup)
        assertEquals(listOf(GroupA), nav.state.stacksInUse)
    }

    @Test
    fun navigatePushesWithinSameGroup() {
        val nav = navigator()

        nav.navigate(pageA1)

        assertEquals(GroupA, nav.state.currentGroup)
        assertEquals(pageA1, nav.state.current)
        assertEquals(listOf(home, pageA1), nav.state.entries.toList())
    }

    @Test
    fun navigateToNewGroupUsesExitThroughHomeEntries() {
        val nav = navigator()
        nav.navigate(pageA1)
        nav.navigate(pageB1)

        assertEquals(GroupB, nav.state.currentGroup)
        assertEquals(pageB1, nav.state.current)
        // start stack + current group stack (not full visit history)
        assertEquals(listOf(home, pageA1, pageB1), nav.state.entries.toList())
        assertEquals(listOf(GroupA, GroupB), nav.state.stacksInUse)
        assertTrue(GroupB in nav.state.backStacks)
    }

    @Test
    fun navigateAThenBThenCKeepsOnlyStartAndCurrentInUse() {
        val nav = navigator()
        val pageC1 = TestDestination("page-c1", GroupId("c"))

        nav.navigate(pageA1)
        nav.navigate(pageB1)
        nav.navigate(pageC1)

        assertEquals(listOf(GroupA, GroupId("c")), nav.state.stacksInUse)
        assertEquals(listOf(home, pageA1, pageC1), nav.state.entries.toList())
        // B stack retained off-screen
        assertEquals(listOf(pageB1), nav.state.backStacks.getValue(GroupB).toList())
    }

    @Test
    fun navigateToGroupRootOnlySwitchesGroup() {
        val nav = navigator()
        nav.navigate(pageBRoot)
        nav.navigate(pageB1)
        nav.navigate(home)
        nav.navigate(pageBRoot)

        assertEquals(GroupB, nav.state.currentGroup)
        assertEquals(listOf(pageBRoot, pageB1), nav.state.backStacks.getValue(GroupB).toList())
        assertEquals(pageB1, nav.state.current)
        assertEquals(listOf(home, pageBRoot, pageB1), nav.state.entries.toList())
    }

    @Test
    fun goBackPopsWithinGroup() {
        val nav = navigator()
        nav.navigate(pageA1)
        nav.navigate(pageA2)
        nav.goBack()

        assertEquals(listOf(home, pageA1), nav.state.entries.toList())
        assertEquals(pageA1, nav.state.current)
    }

    @Test
    fun goBackAtNonStartGroupRootReturnsToStart() {
        val nav = navigator()
        nav.navigate(pageB1)
        nav.goBack()

        assertEquals(GroupA, nav.state.currentGroup)
        assertEquals(listOf(home), nav.state.entries.toList())
        assertEquals(listOf(pageB1), nav.state.backStacks.getValue(GroupB).toList())
    }

    @Test
    fun goBackAtStartRootIsNoOp() {
        val nav = navigator()
        nav.goBack()

        assertEquals(listOf(home), nav.state.entries.toList())
    }

    @Test
    fun popToOrNavigatePushesWithinSameGroup() {
        val nav = navigator()

        nav.popToOrNavigate(pageA1)

        assertEquals(GroupA, nav.state.currentGroup)
        assertEquals(listOf(home, pageA1), nav.state.entries.toList())
    }

    @Test
    fun popToOrNavigatePopsToExistingEntry() {
        val nav = navigator()
        nav.popToOrNavigate(pageA1)
        nav.popToOrNavigate(pageA2)
        nav.popToOrNavigate(pageA1)

        assertEquals(listOf(home, pageA1), nav.state.entries.toList())
    }

    @Test
    fun popToOrNavigateAcrossGroupsRestoresStack() {
        val nav = navigator()
        nav.popToOrNavigate(pageA1)
        nav.popToOrNavigate(pageB1)
        nav.popToOrNavigate(pageA1)

        assertEquals(GroupA, nav.state.currentGroup)
        assertEquals(listOf(home, pageA1), nav.state.entries.toList())
        assertEquals(listOf(pageB1), nav.state.backStacks.getValue(GroupB).toList())
    }

    @Test
    fun popToOrNavigateToStartRootTruncatesStartStack() {
        val nav = navigator()
        nav.popToOrNavigate(pageA1)
        nav.popToOrNavigate(pageA2)
        nav.popToOrNavigate(home)

        assertEquals(GroupA, nav.state.currentGroup)
        assertEquals(listOf(home), nav.state.entries.toList())
    }

    @Test
    fun entriesIsStableWhenStateUnchanged() {
        val nav = navigator()
        nav.navigate(pageA1)

        val first = nav.state.entries
        val second = nav.state.entries

        assertSame(first, second)
    }

    @Test
    fun entriesReflectMutationsWithStableInstance() {
        val nav = navigator()
        nav.navigate(pageA1)
        val view = nav.state.entries

        Snapshot.withMutableSnapshot {
            nav.navigate(pageA2)
        }

        assertSame(view, nav.state.entries)
        assertEquals(listOf(home, pageA1, pageA2), nav.state.entries.toList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun entriesViewTracksSnapshotChanges() = runTest {
        val nav = navigator()
        val emissions = mutableListOf<List<TestDestination>>()

        val job =
            launch(UnconfinedTestDispatcher(testScheduler)) {
                snapshotFlow { nav.state.entries.toList() }
                    .collect { emissions.add(it.map { dest -> dest as TestDestination }) }
            }

        Snapshot.withMutableSnapshot {
            nav.navigate(pageA1)
        }
        Snapshot.withMutableSnapshot {
            nav.navigate(pageA2)
        }

        job.cancel()

        assertTrue(emissions.any { it == listOf(home, pageA1) })
        assertEquals(listOf(home, pageA1, pageA2), emissions.last())
    }
}
