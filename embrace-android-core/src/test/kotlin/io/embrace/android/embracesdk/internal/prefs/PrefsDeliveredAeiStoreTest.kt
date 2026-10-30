package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class PrefsDeliveredAeiStoreTest {

    private lateinit var kvStore: FakeKeyValueStore
    private lateinit var store: PrefsDeliveredAeiStore

    @Before
    fun setUp() {
        kvStore = FakeKeyValueStore()
        store = PrefsDeliveredAeiStore(kvStore)
    }

    @Test
    fun `empty store has no ids`() {
        assertEquals(emptySet<String>(), store.ids())
    }

    @Test
    fun `legacy ids are read under the old key`() {
        kvStore.editAndCommit { putStringSet(KEY, setOf("a", "b")) }
        assertEquals(setOf("a", "b"), store.ids())
    }

    @Test
    fun `add appends to the ids under the old key`() {
        store.add("a")
        store.add("b")
        assertEquals(mapOf(KEY to setOf("a", "b")), kvStore.values())
    }

    @Test
    fun `retainOnly discards ids that are not retained`() {
        kvStore.editAndCommit { putStringSet(KEY, setOf("a", "b")) }
        store.retainOnly(setOf("b", "c"))
        assertEquals(mapOf(KEY to setOf("b")), kvStore.values())
    }

    private companion object {
        const val KEY = "io.embrace.aeiHashCode"
    }
}
