package dev.stashy.navigation

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * A group-aware navigation back stack.
 *
 * This type also implements [MutableList] as a view of the active group's stack.
 */
public class GroupBackStack<Key : GroupNavKey<Group>, Group : Any>(
    public val initial: Key,
    public val stacks: SnapshotStateList<GroupStack<Key, Group>> = mutableStateListOf(),
) : AbstractMutableList<Key>() {
    private val initialGroup =
        initial.group ?: throw IllegalArgumentException("Initial entry must have group")

    public val currentGroup: Group
        get() = stacks.lastOrNull()?.group ?: initialGroup

    private val flatSnapshot: List<Key> by derivedStateOf {
        buildList {
            add(initial)
            stacks.forEach { addAll(it.entries) }
        }
    }

    public val flattened: List<Key> = FlattenedBackStackView { flatSnapshot }

    public val current: Key
        get() = last()

    private val viewOffset: Int
        get() = if (currentGroup == initialGroup) 1 else 0

    private val activeStack: SnapshotStateList<Key>?
        get() = stacks.lastOrNull()?.entries

    override val size: Int
        get() = viewOffset + (activeStack?.size ?: 0)

    override fun get(index: Int): Key {
        checkIndex(index)
        if (viewOffset == 1 && index == 0) return initial
        return activeStack!![index - viewOffset]
    }

    override fun add(element: Key): Boolean {
        val group = element.group
        if (group != null && group != currentGroup) {
            activate(group)
        } else if (group != null) {
            ensureActiveStack(group)
        } else {
            ensureActiveStack(currentGroup)
        }

        val stack = activeStack!!
        if (stack.lastOrNull() != element) {
            stack.add(element)
        }
        return true
    }

    override fun add(index: Int, element: Key) {
        requireGroupForStackOp(element)
        if (index < viewOffset) {
            throw IndexOutOfBoundsException("Cannot insert before the root entry")
        }
        ensureActiveStack(currentGroup)
        activeStack!!.add(index - viewOffset, element)
    }

    override fun addAll(elements: Collection<Key>): Boolean {
        var changed = false
        for (element in elements) {
            changed = add(element) || changed
        }
        return changed
    }

    override fun addAll(index: Int, elements: Collection<Key>): Boolean {
        if (elements.isEmpty()) return false
        if (index < viewOffset) {
            throw IndexOutOfBoundsException("Cannot insert before the root entry")
        }
        ensureActiveStack(currentGroup)
        elements.forEach { requireGroupForStackOp(it) }
        activeStack!!.addAll(index - viewOffset, elements)
        return true
    }

    override fun set(index: Int, element: Key): Key {
        checkIndex(index)
        if (index < viewOffset) {
            throw IndexOutOfBoundsException("Cannot replace the root entry")
        }
        if (index == lastIndex) {
            val old = get(index)
            replace(element)
            return old
        }
        requireGroupForStackOp(element)
        return activeStack!!.set(index - viewOffset, element)
    }

    override fun removeAt(index: Int): Key {
        checkIndex(index)
        if (index < viewOffset) {
            throw IndexOutOfBoundsException("Cannot remove the root entry")
        }
        val removed = activeStack!!.removeAt(index - viewOffset)
        if (activeStack!!.isEmpty()) {
            pruneActiveStack()
        }
        return removed
    }

    /**
     * Pops the top entry of the active stack. No-op when already at the root ([isAtRoot]).
     */
    public fun removeLast() {
        if (isAtRoot()) return
        removeAt(lastIndex)
    }

    public fun replace(entry: Key) {
        if (!isAtRoot()) {
            activeStack!!.removeAt(activeStack!!.lastIndex)
            if (activeStack!!.isEmpty()) {
                pruneActiveStack()
            }
        }

        add(entry)
    }

    public fun swapToGroup(root: Key) {
        val group = root.group ?: throw IllegalArgumentException("Entry must have group")
        activate(group)
        val stack = activeStack ?: return
        val onlyEmptyInitialSlot =
            currentGroup == initialGroup && stacks.size == 1 && stack.isEmpty()
        if (stack.isEmpty() && !onlyEmptyInitialSlot) {
            stack.add(root)
        }
    }

    /**
     * Browser-history style sync: pop back to [entry] in [flattened] if present, otherwise push it.
     */
    public fun syncTo(entry: Key) {
        if (flatSnapshot.lastOrNull() == entry) return

        if (entry == initial) {
            stacks.clear()
            return
        }

        val index = flatSnapshot.lastIndexOf(entry)
        if (index < 0) {
            add(entry)
            return
        }

        truncateFlattenedTo(index)
    }

    public fun isAtRoot(): Boolean = flatSnapshot.size <= 1

    override fun clear() {
        val stack = activeStack ?: return
        stack.clear()
        pruneActiveStack()
    }

    private fun truncateFlattenedTo(index: Int) {
        require(index in flatSnapshot.indices) { "index out of range" }
        if (index == 0) {
            stacks.clear()
            return
        }

        var flatIndex = 1
        var stackIndex = 0
        while (stackIndex < stacks.size) {
            val groupStack = stacks[stackIndex]
            val groupSize = groupStack.entries.size
            val groupEnd = flatIndex + groupSize - 1
            if (index <= groupEnd) {
                val keepCount = index - flatIndex + 1
                while (groupStack.entries.size > keepCount) {
                    groupStack.entries.removeAt(groupStack.entries.lastIndex)
                }
                while (stacks.size > stackIndex + 1) {
                    stacks.removeAt(stacks.lastIndex)
                }
                return
            }
            flatIndex += groupSize
            stackIndex++
        }
    }

    private fun activate(group: Group) {
        val existingIndex = stacks.indexOfFirst { it.group == group }
        if (existingIndex >= 0) {
            val existing = stacks.removeAt(existingIndex)
            stacks.add(existing)
        } else {
            stacks.add(GroupStack(group))
        }
    }

    private fun ensureActiveStack(group: Group) {
        if (stacks.lastOrNull()?.group == group) return
        activate(group)
    }

    private fun pruneActiveStack() {
        if (stacks.isNotEmpty()) {
            stacks.removeAt(stacks.lastIndex)
        }
    }

    private fun requireGroupForStackOp(element: Key) {
        val group = element.group
        if (group != null && group != currentGroup) {
            throw IllegalArgumentException(
                "Entry group $group does not match active group $currentGroup; use add() to switch groups",
            )
        }
    }

    private fun checkIndex(index: Int) {
        if (index !in indices) {
            throw IndexOutOfBoundsException("index: $index, size: $size")
        }
    }
}
