@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.arch.store

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.prefs.SharedPrefsStore
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class KeyValueStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: KeyValueStore

    @Before
    fun setUp() {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        store = SharedPrefsStore(prefs, TestPlatformSerializer())
    }

    @Test
    fun testDefaultRead() {
        val key = "key"
        assertNull(store.getString(key))
        assertNull(store.getInt(key))
        assertNull(store.getLong(key))
        assertFalse(checkNotNull(store.getBoolean(key, false)))
        assertNull(store.getStringSet(key))
        assertNull(store.getStringMap(key))
    }

    @Test
    fun testOverrideValues() {
        val strValue = "value"
        val intValue = 1
        val longValue = 2L
        val boolValue = true
        val setValue = setOf("a", "b")
        val mapValue = mapOf("a" to "b")

        store.editAndCommit {
            putString("string", strValue)
            putInt("int", intValue)
            putLong("long", longValue)
            putBoolean("bool", boolValue)
            putStringSet("set", setValue)
            putStringMap("map", mapValue)
        }
        assertEquals(strValue, store.getString("string"))
        assertEquals(intValue, store.getInt("int"))
        assertEquals(longValue, store.getLong("long"))
        assertEquals(boolValue, store.getBoolean("bool", false))
        assertEquals(setValue, store.getStringSet("set"))
        assertEquals(mapValue, store.getStringMap("map"))
    }

    @Test
    fun `values written in a batch are readable before and after it commits`() {
        val setValue = setOf("a", "b")
        val mapValue = mapOf("a" to "b")

        store.batch {
            store.editAndCommit { putString("string", "value") }
            store.editAndCommit { putInt("int", 1) }
            store.editAndCommit { putLong("long", 2L) }
            store.editAndCommit { putBoolean("bool", true) }
            store.editAndCommit { putStringSet("set", setValue) }
            store.editAndCommit { putStringMap("map", mapValue) }

            assertEquals("value", store.getString("string"))
            assertEquals(1, store.getInt("int"))
            assertEquals(2L, store.getLong("long"))
            assertTrue(store.getBoolean("bool", false))
            assertEquals(setValue, store.getStringSet("set"))
            assertEquals(mapValue, store.getStringMap("map"))
        }

        assertEquals("value", store.getString("string"))
        assertEquals(1, store.getInt("int"))
        assertEquals(2L, store.getLong("long"))
        assertTrue(store.getBoolean("bool", false))
        assertEquals(setValue, store.getStringSet("set"))
        assertEquals(mapValue, store.getStringMap("map"))
    }

    @Test
    fun `a batch reads through to committed values it has not written`() {
        store.editAndCommit { putString("string", "committed") }

        store.batch {
            assertEquals("committed", store.getString("string"))
            store.editAndCommit { putString("other", "pending") }
            assertEquals("committed", store.getString("string"))
        }
    }

    @Test
    fun `a null written in a batch shadows a committed value`() {
        store.editAndCommit {
            putString("string", "committed")
            putStringSet("set", setOf("a"))
            putStringMap("map", mapOf("a" to "b"))
            putInt("int", 1)
            putLong("long", 2L)
            putBoolean("bool", true)
        }

        store.batch {
            store.editAndCommit {
                putString("string", null)
                putStringSet("set", null)
                putStringMap("map", null)
                putInt("int", null)
                putLong("long", null)
                putBoolean("bool", null)
            }
            assertNull(store.getString("string"))
            assertNull(store.getStringSet("set"))
            assertNull(store.getStringMap("map"))
            assertNull(store.getInt("int"))
            assertNull(store.getLong("long"))
            assertFalse(store.getBoolean("bool", true))
        }

        assertNull(store.getString("string"))
        assertNull(store.getStringSet("set"))
        assertNull(store.getStringMap("map"))
        assertNull(store.getInt("int"))
        assertNull(store.getLong("long"))
        assertFalse(store.getBoolean("bool", true))
    }

    @Test
    fun `the last write to a key in a batch wins`() {
        store.batch {
            store.editAndCommit { putStringMap("map", mapOf("a" to "b")) }
            store.editAndCommit { putStringMap("map", mapOf("c" to "d")) }
        }
        assertEquals(mapOf("c" to "d"), store.getStringMap("map"))
    }

    @Test
    fun `a nested batch defers its commit to the outer batch`() {
        store.batch {
            store.editAndCommit { putString("outer", "a") }
            store.batch {
                store.editAndCommit { putString("inner", "b") }
            }
            // the inner batch joined the outer one, so nothing has been committed yet
            assertEquals("b", store.getString("inner"))
        }
        assertEquals("a", store.getString("outer"))
        assertEquals("b", store.getString("inner"))
    }

    @Test
    fun `a batch commits what it buffered even if the action throws`() {
        assertThrows(IllegalStateException::class.java) {
            store.batch {
                store.editAndCommit { putString("string", "value") }
                error("boom")
            }
        }
        assertEquals("value", store.getString("string"))

        // the failed batch was cleaned up, so subsequent edits commit immediately again
        store.editAndCommit { putString("other", "value") }
        assertEquals("value", store.getString("other"))
    }
}
