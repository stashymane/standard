package dev.stashy.navigation.state

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Holds per–nav-entry state as JSON strings, keyed by [NavEntry.contentKey][androidx.navigation3.runtime.NavEntry.contentKey]
 * and logical state keys from [rememberNavEntryState][rememberNavEntryState].
 */
public class EntryStateStore {
    private val states: MutableMap<String, MutableMap<String, String>> = mutableMapOf()

    /**
     * Emits when entry state changes. Used on web to debounce writes to `history.state`.
     */
    public val changes: SharedFlow<Unit>
        field = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Snapshot of all entry state for persistence (for example browser `history.state` on web).
     */
    public fun snapshot(): Map<String, Map<String, String>> =
        states.mapValues { it.value.toMap() }

    /**
     * Replaces all stored entry state (for example after reading `history.state` on web).
     */
    public fun restore(snapshot: Map<String, Map<String, String>>) {
        states.clear()
        snapshot.forEach { (entryId, entry) ->
            states[entryId] = entry.toMutableMap()
        }
        changes.tryEmit(Unit)
    }

    public fun getString(entryId: String, key: String): String? = states[entryId]?.get(key)

    public fun setString(entryId: String, key: String, json: String) {
        val entry = states.getOrPut(entryId) { mutableMapOf() }
        entry[key] = json
        changes.tryEmit(Unit)
    }

    public fun remove(entryId: String) {
        states.remove(entryId)
        changes.tryEmit(Unit)
    }
}
