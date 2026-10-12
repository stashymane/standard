package dev.stashy.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.StateObject
import androidx.compose.runtime.snapshots.StateRecord

public class GroupBackStack<Key : GroupNavKey<Group>, Group : Any>(
    private val initial: Key,
    private val base: SnapshotStateList<Key> = mutableStateListOf(initial),
) : MutableList<Key> by base, StateObject {
    private val stacks = linkedMapOf<Group, MutableList<Key>>()
    private val initialGroup =
        initial.group ?: throw IllegalArgumentException("Initial entry must have group")

    public var currentGroup: Group by mutableStateOf(initialGroup)
        private set
    private var currentStack = mutableListOf<Key>()

    public val current: Key
        get() = last()

    override fun add(element: Key): Boolean {
        val group = element.group
        if (group != null && group != currentGroup)
            swapTo(group)

        if (currentStack.lastOrNull() != element)
            currentStack.add(element)

        update()
        return true
    }

    public fun removeLast() {
        currentStack.removeLastOrNull()
        if (currentStack.isEmpty())
            stacks.keys.lastOrNull()?.let(::swapTo)
        if (currentStack.isEmpty())
            reset()

        update()
    }

    public fun replace(entry: Key) {
        currentStack.removeLastOrNull()

        val group = entry.group
        if (group != null && group != currentGroup)
            swapTo(group)

        currentStack.add(entry)

        update()
    }

    public fun swapToGroup(root: Key) {
        val group = root.group ?: throw IllegalArgumentException("Entry must have group")
        swapTo(group)
        if (currentStack.isEmpty() && !(currentGroup == initialGroup && stacks.isEmpty()))
            currentStack.add(root)

        update()
    }

    override fun removeAt(index: Int): Key {
        check(index == lastIndex) {
            "GroupBackStack only supports removing the top entry (index must be lastIndex)"
        }
        val removed = get(index)
        removeLast()
        return removed
    }

    private fun swapTo(group: Group) {
        if (currentStack.isNotEmpty())
            stacks[currentGroup] = currentStack
        currentStack = stacks.remove(group) ?: mutableListOf()
        currentGroup = group
    }

    private fun reset() {
        currentGroup = initialGroup
        currentStack = mutableListOf()
    }

    private fun update() {
        base.apply {
            clear()
            add(initial)
            addAll(stacks.flatMap(Map.Entry<Group, List<Key>>::value))
            addAll(currentStack)
        }
    }

    public fun isAtRoot(): Boolean = size <= 1

    override val firstStateRecord: StateRecord
        get() = base.firstStateRecord

    override fun prependStateRecord(value: StateRecord) {
        base.prependStateRecord(value)
    }

    override fun mergeRecords(
        previous: StateRecord,
        current: StateRecord,
        applied: StateRecord,
    ): StateRecord? = base.mergeRecords(previous, current, applied)
}
