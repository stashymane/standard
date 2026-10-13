package dev.stashy.navigation

import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

private enum class TestGroup {
    A,
    B,
}

private data class TestKey(
    val name: String,
    override val group: TestGroup?,
) : GroupNavKey<TestGroup>

class GroupBackStackBrowserSyncTest {
    private val home = TestKey("home", TestGroup.A)
    private val pageA1 = TestKey("page-a1", TestGroup.A)
    private val pageA2 = TestKey("page-a2", TestGroup.A)
    private val pageB1 = TestKey("page-b1", TestGroup.B)

    @Test
    fun rootViewShowsInitialOnly() {
        val stack = GroupBackStack(home)

        assertEquals(listOf(home), stack.toList())
        assertEquals(listOf(home), stack.flattened)
        assertEquals(home, stack.current)
    }

    @Test
    fun syncToPushesWithinSameGroup() {
        val stack = GroupBackStack(home)

        stack.syncTo(pageA1)

        assertEquals(TestGroup.A, stack.currentGroup)
        assertEquals(pageA1, stack.current)
        assertEquals(listOf(home, pageA1), stack.flattened)
        assertEquals(listOf(home, pageA1), stack.toList())
        assertFalse(stack.isAtRoot())
    }

    @Test
    fun syncToNewGroupRetainsPriorEntriesInFlatStack() {
        val stack = GroupBackStack(home)
        stack.syncTo(pageA1)
        stack.syncTo(pageB1)

        assertEquals(TestGroup.B, stack.currentGroup)
        assertEquals(pageB1, stack.current)
        assertEquals(listOf(home, pageA1, pageB1), stack.flattened)
        assertEquals(listOf(pageB1), stack.toList())
    }

    @Test
    fun syncToExistingEntryInCurrentGroupPopsCurrentStack() {
        val stack = GroupBackStack(home)
        stack.syncTo(pageA1)
        stack.syncTo(pageA2)
        stack.syncTo(pageA1)

        assertEquals(TestGroup.A, stack.currentGroup)
        assertEquals(pageA1, stack.current)
        assertEquals(listOf(home, pageA1), stack.flattened)
        assertEquals(listOf(home, pageA1), stack.toList())
    }

    @Test
    fun syncToEntryInInactiveGroupRestoresGroupAndStack() {
        val stack = GroupBackStack(home)
        stack.syncTo(pageA1)
        stack.syncTo(pageB1)
        stack.syncTo(pageA1)

        assertEquals(TestGroup.A, stack.currentGroup)
        assertEquals(pageA1, stack.current)
        assertEquals(listOf(home, pageA1), stack.flattened)
    }

    @Test
    fun syncToRootClearsNavigationStacks() {
        val stack = GroupBackStack(home)
        stack.syncTo(pageA1)
        stack.syncTo(pageA2)
        stack.syncTo(home)

        assertEquals(TestGroup.A, stack.currentGroup)
        assertEquals(home, stack.current)
        assertEquals(listOf(home), stack.flattened)
        assertEquals(true, stack.isAtRoot())
    }

    @Test
    fun reactivatingInitialGroupPreservesFlatOrder() {
        val stack = GroupBackStack(home)
        stack.syncTo(pageA1)
        stack.syncTo(pageB1)
        stack.syncTo(pageA1)

        assertEquals(listOf(home, pageA1), stack.flattened)
    }

    @Test
    fun replaceAcrossGroups() {
        val stack = GroupBackStack(home)
        stack.syncTo(pageA1)
        stack.replace(pageB1)

        assertEquals(TestGroup.B, stack.currentGroup)
        assertEquals(pageB1, stack.current)
        assertEquals(listOf(home, pageB1), stack.flattened)
    }

    @Test
    fun swapToGroupRestoresExistingStack() {
        val stack = GroupBackStack(home)
        stack.syncTo(pageA1)
        stack.syncTo(pageB1)
        stack.swapToGroup(home)

        assertEquals(TestGroup.A, stack.currentGroup)
        assertEquals(listOf(home, pageB1, pageA1), stack.flattened)
        assertEquals(pageA1, stack.current)
        assertEquals(listOf(home, pageA1), stack.toList())
    }

    @Test
    fun addWithMismatchedGroupOnIndexedAddThrows() {
        val stack = GroupBackStack(home)
        stack.syncTo(pageA1)

        assertFailsWith<IllegalArgumentException> {
            stack.add(1, pageB1)
        }
    }

    @Test
    fun cannotRemoveRootFromView() {
        val stack = GroupBackStack(home)
        stack.syncTo(pageA1)

        assertFailsWith<IndexOutOfBoundsException> {
            stack.removeAt(0)
        }
    }

    @Test
    fun removeLastAtRootIsNoOp() {
        val stack = GroupBackStack(home)

        stack.removeLast()

        assertEquals(listOf(home), stack.flattened)
    }

    @Test
    fun flattenedIsStableWhenStateUnchanged() {
        val stack = GroupBackStack(home)
        stack.syncTo(pageA1)

        val first = stack.flattened
        val second = stack.flattened

        assertSame(first, second)
    }

    @Test
    fun flattenedReflectsMutationsWithStableInstance() {
        val stack = GroupBackStack(home)
        stack.syncTo(pageA1)
        val view = stack.flattened

        Snapshot.withMutableSnapshot {
            stack.syncTo(pageA2)
        }

        assertSame(view, stack.flattened)
        assertEquals(listOf(home, pageA1, pageA2), stack.flattened.toList())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun flattenedViewTracksSnapshotChanges() = runTest {
        val stack = GroupBackStack(home)
        val emissions = mutableListOf<List<TestKey>>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            snapshotFlow { stack.flattened.toList() }
                .collect { emissions.add(it) }
        }

        Snapshot.withMutableSnapshot {
            stack.syncTo(pageA1)
        }
        Snapshot.withMutableSnapshot {
            stack.syncTo(pageA2)
        }

        job.cancel()

        assertTrue(emissions.any { it == listOf(home, pageA1) })
        assertEquals(listOf(home, pageA1, pageA2), emissions.last())
    }
}
