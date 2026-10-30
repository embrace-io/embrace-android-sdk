package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class PrefsNetworkCaptureRuleStoreTest {

    private lateinit var kvStore: FakeKeyValueStore
    private lateinit var store: PrefsNetworkCaptureRuleStore

    @Before
    fun setUp() {
        kvStore = FakeKeyValueStore()
        store = PrefsNetworkCaptureRuleStore(kvStore)
    }

    @Test
    fun `missing rule returns the default`() {
        assertEquals(5, store.remaining("rule", 5))
    }

    @Test
    fun `legacy count is read under the old key`() {
        kvStore.editAndCommit { putInt(KEY, 2) }
        assertEquals(2, store.remaining("rule", 5))
    }

    @Test
    fun `decrement starts from the default and persists under the old key`() {
        store.decrement("rule", 5)
        store.decrement("rule", 5)
        assertEquals(mapOf(KEY to 3), kvStore.values())
        assertEquals(3, store.remaining("rule", 5))
    }

    @Test
    fun `decrement does not go below zero`() {
        store.decrement("rule", 1)
        store.decrement("rule", 1)
        assertEquals(mapOf(KEY to 0), kvStore.values())
        assertEquals(0, store.remaining("rule", 1))
    }

    private companion object {
        const val KEY = "io.embrace.networkcapturerulerule"
    }
}
