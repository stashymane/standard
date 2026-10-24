package dev.stashy.navigation.state

/**
 * Scope for reading and writing state for the current [NavEntry][androidx.navigation3.runtime.NavEntry].
 *
 * Provided by [dev.stashy.navigation.compose.rememberEntryStateNavEntryDecorator].
 */
public class EntryStateScope internal constructor(
    public val store: EntryStateStore,
    internal val entryId: String,
) {
    internal companion object {
        internal const val MAX_RADIX = 36
    }
}
