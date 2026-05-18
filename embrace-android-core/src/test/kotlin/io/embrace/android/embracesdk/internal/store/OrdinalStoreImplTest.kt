package io.embrace.android.embracesdk.internal.store

import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OrdinalStoreImplTest {

    private lateinit var kvStore: FakeKeyValueStore
    private lateinit var store: OrdinalStore

    @Before
    fun setUp() {
        kvStore = FakeKeyValueStore()
        store = OrdinalStoreImpl(kvStore)
    }

    @Test
    fun `test ordinal numbers are saved`() {
        Ordinal.entries.filter { it != Ordinal.USER_SESSION }.forEach { ordinal ->
            repeat(4) { k ->
                assertEquals(k + 1, store.incrementAndGet(ordinal))
            }
        }
    }

    @Test
    fun `user session counter seeded from legacy session counter on upgrade`() {
        kvStore.edit { putInt(Ordinal.SESSION.key, 42) }
        assertEquals(43, store.incrementAndGet(Ordinal.USER_SESSION))
        assertEquals(44, store.incrementAndGet(Ordinal.USER_SESSION))
    }

    @Test
    fun `user session counter starts at 1 on fresh install`() {
        assertEquals(1, store.incrementAndGet(Ordinal.USER_SESSION))
        assertEquals(2, store.incrementAndGet(Ordinal.USER_SESSION))
    }

    @Test
    fun `seed lambda only fires on the first call for an ordinal`() {
        var seedCalls = 0
        val seed = {
            seedCalls++
            5
        }
        assertEquals(5, store.incrementAndGet(Ordinal.SESSION_PART, seed))
        assertEquals(6, store.incrementAndGet(Ordinal.SESSION_PART, seed))
        // Even with a different seed value, subsequent calls just increment.
        assertEquals(7, store.incrementAndGet(Ordinal.SESSION_PART) { 100 })
        assertEquals(1, seedCalls)
    }

    @Test
    fun `default seed makes indices start at 1`() {
        assertEquals(1, store.incrementAndGet(Ordinal.SESSION_PART))
        assertEquals(2, store.incrementAndGet(Ordinal.SESSION_PART))
    }
}
