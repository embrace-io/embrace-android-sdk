package io.embrace.android.embracesdk.internal.instrumentation.aei

import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class AeiDataStoreImplTest {

    private lateinit var store: AeiDataStore

    @Before
    fun setUp() {
        store = AeiDataStoreImpl(FakeKeyValueStore())
    }

    @Test
    fun `test aei delivered ids`() {
        val expected = setOf("a")
        store.deliveredAeiIds = expected
        assertEquals(expected, store.deliveredAeiIds)
    }

    @Test
    fun `test aei crash number is saved`() {
        assertEquals(1, store.incrementAndGetAeiCrashNumber())
        assertEquals(2, store.incrementAndGetAeiCrashNumber())
        assertEquals(3, store.incrementAndGetAeiCrashNumber())
        assertEquals(4, store.incrementAndGetAeiCrashNumber())
    }

    @Test
    fun `test crash number is saved`() {
        assertEquals(1, store.incrementAndGetCrashNumber())
        assertEquals(2, store.incrementAndGetCrashNumber())
        assertEquals(3, store.incrementAndGetCrashNumber())
        assertEquals(4, store.incrementAndGetCrashNumber())
    }
}
