package dev.stashy.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack

public class GroupBackStack<Key : GroupNavKey<Group>, Group : Any>(
    private val initial: Key
) {
    private val stacks = linkedMapOf<Group, MutableList<Key>>()
    private val initialGroup =
        initial.group ?: throw IllegalArgumentException("Initial entry must have group")

    public var currentGroup: Group by mutableStateOf(initialGroup)
        private set
    private var currentStack = mutableListOf<Key>()

    public val backStack: NavBackStack<Key> = NavBackStack(initial)

    public val current: Key
        get() = backStack.last()

    public fun add(entry: Key) {
        val group = entry.group
        if (group != null && group != currentGroup)
            swapTo(group)

        if (currentStack.lastOrNull() != entry)
            currentStack.add(entry)

        update()
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

    public fun isEmpty(): Boolean = backStack.size <= 1
    public fun isNotEmpty(): Boolean = backStack.size > 1
}
