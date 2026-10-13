package dev.stashy.navigation

/**
 * Stable read-only [List] facade over a snapshot-backed flat back stack.
 *
 * The same instance can be passed to [androidx.compose.runtime.snapshotFlow] and
 * [androidx.compose.runtime.remember] while [readSnapshot] supplies fresh contents
 * when underlying Compose state changes.
 */
internal class FlattenedBackStackView<Key>(
    private val readSnapshot: () -> List<Key>,
) : AbstractList<Key>() {
    override val size: Int
        get() = readSnapshot().size

    override fun get(index: Int): Key = readSnapshot()[index]
}
