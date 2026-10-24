package dev.stashy.navigation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class PersistedEntryStates(
    val entries: Map<String, Map<String, String>> = emptyMap(),
)

private val persistedJson = Json {
    ignoreUnknownKeys = true
}

internal fun encodeEntryStateSnapshot(snapshot: Map<String, Map<String, String>>): String =
    persistedJson.encodeToString(PersistedEntryStates(entries = snapshot))

internal fun decodeEntryStateSnapshot(raw: Any?): Map<String, Map<String, String>> {
    if (raw == null) return emptyMap()
    val json = when (raw) {
        is String -> raw
        else -> raw.toString()
    }
    if (json.isBlank()) return emptyMap()
    return runCatching {
        persistedJson.decodeFromString<PersistedEntryStates>(json).entries
    }.getOrDefault(emptyMap())
}

internal fun hydrateEntryStatesFromHistory(store: EntryStateStore) {
    store.restore(decodeEntryStateSnapshot(readCurrentHistoryState()))
}
