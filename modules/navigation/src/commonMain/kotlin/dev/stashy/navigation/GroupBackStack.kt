package dev.stashy.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack

class GroupBackStack<Key : GroupNavKey<Group>, Group : Any>(
    private val initial: Key
) {
    private val stacks = linkedMapOf<Group, MutableList<Key>>()
    private val initialGroup =
        initial.group ?: throw IllegalArgumentException("Initial entry must have group")

    var currentGroup by mutableStateOf(initialGroup)
        private set
    private var currentStack = mutableListOf<Key>()

    val backStack = NavBackStack(initial)

    val current: Key
        get() = backStack.last()

    fun add(entry: Key) {
        val group = entry.group
        if (group != null && group != currentGroup)
            swapTo(group)

        if (currentStack.lastOrNull() != entry)
            currentStack.add(entry)

        update()
    }

    fun removeLast() {
        currentStack.removeLastOrNull()
        if (currentStack.isEmpty())
            stacks.keys.lastOrNull()?.let(::swapTo)
        if (currentStack.isEmpty())
            reset()

        update()
    }

    fun replace(entry: Key) {
        currentStack.removeLastOrNull()

        val group = entry.group
        if (group != null && group != currentGroup)
            swapTo(group)

        currentStack.add(entry)

        update()
    }

    fun swapToGroup(root: Key) {
        val group = root.group ?: throw IllegalArgumentException("Entry must have group")
        swapTo(group)
        if (currentStack.isEmpty() && !(currentGroup == initialGroup && stacks.isEmpty()))
            currentStack.add(root)

        update()
    }

    /**
     * Move the stack to [entry] the way browser history would:
     * pop until it is current when it already exists, otherwise push it.
     */
    fun syncTo(entry: Key) {
        if (current == entry) return

        if (backStack.lastIndexOf(entry) >= 0) {
            while (current != entry && isNotEmpty())
                removeLast()
            return
        }

        add(entry)
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
        backStack.apply {
            clear()
            add(initial)
            addAll(stacks.flatMap(Map.Entry<Group, List<Key>>::value))
            addAll(currentStack)
        }
    }

    fun isEmpty() = backStack.size <= 1
    fun isNotEmpty() = backStack.size > 1
}
