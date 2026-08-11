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
        Ordinal.entries.filter { it != Ordinal.USER_SESSION && it.scopeKey == null }.forEach { ordinal ->
            repeat(4) { k ->
                assertEquals(k + 1, store.incrementAndGet(ordinal))
            }
        }
    }

    @Test
    fun `user session counter seeded from legacy session counter on upgrade`() {
        kvStore.editAndCommit { putInt(Ordinal.SESSION.key, 42) }
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
        assertEquals(5, store.incrementAndGet(Ordinal.SESSION_PART, seed = seed))
        assertEquals(6, store.incrementAndGet(Ordinal.SESSION_PART, seed = seed))
        // Even with a different seed value, subsequent calls just increment.
        assertEquals(7, store.incrementAndGet(Ordinal.SESSION_PART) { 100 })
        assertEquals(1, seedCalls)
    }

    @Test
    fun `default seed makes indices start at 1`() {
        assertEquals(1, store.incrementAndGet(Ordinal.SESSION_PART))
        assertEquals(2, store.incrementAndGet(Ordinal.SESSION_PART))
    }

    @Test
    fun `scoped counter starts at 1 and increments while the scope is unchanged`() {
        assertEquals(1, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.0"))
        assertEquals(2, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.0"))
        assertEquals(3, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.0"))
    }

    @Test
    fun `scoped counter resets when the scope changes`() {
        assertEquals(1, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.0"))
        assertEquals(2, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.0"))
        assertEquals(1, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.1"))
        assertEquals(2, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.1"))
        // reverting to a previously-seen scope also counts as a scope change
        assertEquals(1, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.0"))
    }

    @Test
    fun `scoped counter seed used on first increment and after a scope change`() {
        assertEquals(10, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.0") { 10 })
        assertEquals(11, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.0") { 10 })
        assertEquals(20, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "2.0.0") { 20 })
    }

    @Test
    fun `scope ignored for an ordinal without a scope key`() {
        assertEquals(1, store.incrementAndGet(Ordinal.SESSION, "1.0.0"))
        assertEquals(2, store.incrementAndGet(Ordinal.SESSION))
        assertEquals(3, store.incrementAndGet(Ordinal.SESSION, "2.0.0"))
    }

    @Test
    fun `omitting the scope for a scoped ordinal returns -1 and does not increment`() {
        assertEquals(-1, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP))
        assertEquals(1, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.0"))
    }

    @Test
    fun `seeding an ordinal only costs one commit`() {
        assertEquals(1, store.incrementAndGet(Ordinal.SESSION_PART))
        assertEquals(1, kvStore.commitCount)

        assertEquals(2, store.incrementAndGet(Ordinal.SESSION_PART))
        assertEquals(2, kvStore.commitCount)
    }

    @Test
    fun `seeding a scoped ordinal only costs one commit`() {
        assertEquals(1, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.0"))
        assertEquals(1, kvStore.commitCount)

        assertEquals(2, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.0"))
        assertEquals(2, kvStore.commitCount)

        assertEquals(1, store.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.1"))
        assertEquals(3, kvStore.commitCount)
    }

    @Test
    fun `increments of different ordinals in a batch share one commit`() {
        kvStore.batch {
            assertEquals(1, store.incrementAndGet(Ordinal.SESSION))
            assertEquals(1, store.incrementAndGet(Ordinal.SESSION_PART))
        }
        assertEquals(1, kvStore.commitCount)
        assertEquals(2, store.incrementAndGet(Ordinal.SESSION))
        assertEquals(2, store.incrementAndGet(Ordinal.SESSION_PART))
    }

    @Test
    fun `repeated increments of one ordinal in a batch observe pending writes`() {
        kvStore.batch {
            assertEquals(1, store.incrementAndGet(Ordinal.SESSION_PART))
            assertEquals(2, store.incrementAndGet(Ordinal.SESSION_PART))
            assertEquals(3, store.incrementAndGet(Ordinal.SESSION_PART))
        }
        assertEquals(1, kvStore.commitCount)
        assertEquals(4, store.incrementAndGet(Ordinal.SESSION_PART))
    }

    @Test
    fun `a failure in the underlying store returns -1`() {
        val throwingStore = OrdinalStoreImpl(ThrowingKeyValueStore())
        assertEquals(-1, throwingStore.incrementAndGet(Ordinal.SESSION))
        assertEquals(-1, throwingStore.incrementAndGet(Ordinal.APP_VERSION_STARTUP, "1.0.0"))
    }

    /**
     * Mimics [android.content.SharedPreferences] throwing when a key holds a value of another type.
     */
    private class ThrowingKeyValueStore : KeyValueStore {
        override fun getString(key: String): String? = error("simulated store failure")
        override fun getInt(key: String): Int? = error("simulated store failure")
        override fun getLong(key: String): Long? = error("simulated store failure")
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = error("simulated store failure")
        override fun getStringSet(key: String): Set<String>? = error("simulated store failure")
        override fun getStringMap(key: String): Map<String, String>? = error("simulated store failure")
        override fun editAndCommit(action: KeyValueStoreEditor.() -> Unit) = error("simulated store failure")
    }
}
