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
}
