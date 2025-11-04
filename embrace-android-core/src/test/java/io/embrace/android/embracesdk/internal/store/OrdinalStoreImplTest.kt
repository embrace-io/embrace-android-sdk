package io.embrace.android.embracesdk.internal.store

import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OrdinalStoreImplTest {

    private lateinit var store: OrdinalStore

    @Before
    fun setUp() {
        store = OrdinalStoreImpl(FakeKeyValueStore())
    }

    @Test
    fun `test ordinal numbers are saved`() {
        Ordinal.entries.forEach { ordinal ->
            repeat(4) { k ->
                assertEquals(k + 1, store.incrementAndGet(ordinal))
            }
        }
    }
}
