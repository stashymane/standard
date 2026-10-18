package dev.stashy.navigation

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * Holds per–nav-entry state as JSON strings, keyed by [NavEntry.contentKey][androidx.navigation3.runtime.NavEntry.contentKey]
 * and logical state keys from [rememberNavEntryState][dev.stashy.navigation.compose.rememberNavEntryState].
 */
public class EntryStateStore {
    private val states: SnapshotStateMap<String, Map<String, String>> = mutableStateMapOf()

    /**
     * Snapshot of all entry state for persistence (for example browser `history.state` on web).
     */
    public fun snapshot(): Map<String, Map<String, String>> = states.toMap()

    /**
     * Replaces all stored entry state (for example after reading `history.state` on web).
     */
    public fun restore(snapshot: Map<String, Map<String, String>>) {
        states.clear()
        states.putAll(snapshot)
    }

    public fun getString(entryId: String, key: String): String? = states[entryId]?.get(key)

    public fun setString(entryId: String, key: String, json: String) {
        val entry = states[entryId].orEmpty().toMutableMap()
        entry[key] = json
        states[entryId] = entry
    }

    public fun remove(entryId: String) {
        states.remove(entryId)
    }
}
