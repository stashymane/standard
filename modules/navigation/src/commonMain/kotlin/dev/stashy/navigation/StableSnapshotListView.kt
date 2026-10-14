package dev.stashy.navigation

/**
 * Provides a stable view over a list, including derived state lists.
 */
internal class StableSnapshotListView<Key>(
    private val readSnapshot: () -> List<Key>,
) : AbstractList<Key>() {
    override val size: Int
        get() = readSnapshot().size

    override fun get(index: Int): Key = readSnapshot()[index]
}
