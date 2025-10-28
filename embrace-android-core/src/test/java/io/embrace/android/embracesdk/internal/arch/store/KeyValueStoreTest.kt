@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.arch.store

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.prefs.SharedPrefsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun testIncrementAndGet() {
        val key = "a"
        val otherKey = "b"
        assertEquals(1, store.incrementAndGet(key))
        assertEquals(2, store.incrementAndGet(key))
        assertEquals(1, store.incrementAndGet(otherKey))
    }

    @Test
    fun testOverrideValues() {
        val strValue = "value"
        val intValue = 1
        val longValue = 2L
        val boolValue = true
        val setValue = setOf("a", "b")
        val mapValue = mapOf("a" to "b")

        store.edit {
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
}
