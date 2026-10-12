package dev.stashy.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private enum class TestGroup {
    A,
    B,
}

private data class TestKey(
    val name: String,
    override val group: TestGroup?,
) : GroupNavKey<TestGroup>

@OptIn(InternalNavApi::class)
class GroupBackStackBrowserSyncTest {
    private val home = TestKey("home", TestGroup.A)
    private val pageA1 = TestKey("page-a1", TestGroup.A)
    private val pageA2 = TestKey("page-a2", TestGroup.A)
    private val pageB1 = TestKey("page-b1", TestGroup.B)

    @Test
    fun syncToPushesWithinSameGroup() {
        val stack = GroupBackStack(home)

        stack.syncTo(pageA1)

        assertEquals(TestGroup.A, stack.currentGroup)
        assertEquals(pageA1, stack.current)
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
        assertEquals(listOf(home, pageA1, pageB1), stack.toList())
    }

    @Test
    fun syncToExistingEntryInCurrentGroupPopsCurrentStack() {
        val stack = GroupBackStack(home)
        stack.syncTo(pageA1)
        stack.syncTo(pageA2)
        stack.syncTo(pageA1)

        assertEquals(TestGroup.A, stack.currentGroup)
        assertEquals(pageA1, stack.current)
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
        assertEquals(listOf(home, pageA1), stack.toList())
    }

    @Test
    fun syncToRootClearsNavigationStacks() {
        val stack = GroupBackStack(home)
        stack.syncTo(pageA1)
        stack.syncTo(pageA2)
        stack.syncTo(home)

        assertEquals(TestGroup.A, stack.currentGroup)
        assertEquals(home, stack.current)
        assertEquals(listOf(home), stack.toList())
        assertEquals(true, stack.isAtRoot())
    }
}
