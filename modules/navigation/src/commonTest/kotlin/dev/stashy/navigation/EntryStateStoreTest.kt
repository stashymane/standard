package dev.stashy.navigation

import dev.stashy.navigation.state.EntryStateStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EntryStateStoreTest {
    @Test
    fun `entry state round-trips through snapshots`() {
        val store = EntryStateStore()
        store.setString("entry-a", "field", "\"hello\"")
        assertEquals("\"hello\"", store.getString("entry-a", "field"))

        val snapshot = store.snapshot()
        assertEquals(mapOf("entry-a" to mapOf("field" to "\"hello\"")), snapshot)

        val restored = EntryStateStore()
        restored.restore(snapshot)
        assertEquals("\"hello\"", restored.getString("entry-a", "field"))

        store.remove("entry-a")
        assertNull(store.getString("entry-a", "field"))
        assertEquals(emptyMap(), store.snapshot())
    }

    @Test
    fun `decoding invalid JSON returns null`() {
        val decoded =
            runCatching { NavDefaults.json.decodeFromString<String>("not-json") }.getOrNull()
        assertNull(decoded)
    }

    @Test
    fun `remove clears all entry keys`() {
        val store = EntryStateStore()
        store.setString("entry", "a", "1")
        store.setString("entry", "b", "2")
        store.remove("entry")
        assertEquals(emptyMap(), store.snapshot())
    }
}
