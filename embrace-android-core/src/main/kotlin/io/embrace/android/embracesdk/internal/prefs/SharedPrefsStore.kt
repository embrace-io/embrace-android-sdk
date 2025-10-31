package io.embrace.android.embracesdk.internal.prefs

import android.content.SharedPreferences
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.KeyValueStoreEditor

internal class SharedPrefsStore(
    private val impl: SharedPreferences,
    private val serializer: PlatformSerializer,
) : KeyValueStore {

    override fun getString(key: String): String? {
        return impl.getString(key, null)
    }

    override fun getInt(key: String): Int? {
        val defaultValue: Int = -1
        return when (val value = impl.getInt(key, defaultValue)) {
            defaultValue -> null
            else -> value
        }
    }

    override fun getLong(key: String): Long? {
        val defaultValue: Long = -1L
        return when (val value = impl.getLong(key, defaultValue)) {
            defaultValue -> null
            else -> value
        }
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return impl.getBoolean(key, defaultValue)
    }

    override fun getStringSet(key: String): Set<String>? {
        return impl.getStringSet(key, null)
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringMap(key: String): Map<String, String>? {
        val mapString = impl.getString(key, null) ?: return null
        return serializer.fromJson(mapString, Map::class.java) as Map<String, String>
    }

    override fun edit(action: KeyValueStoreEditor.() -> Unit) {
        SharedPrefsStoreEditor(impl.edit(), serializer).use {
            it.action()
        }
    }

    override fun incrementAndGet(key: String): Int {
        return try {
            val ordinal = (getInt(key) ?: 0) + 1
            edit {
                putInt(key, ordinal)
            }
            ordinal
        } catch (tr: Throwable) {
            -1
        }
    }

    override fun incrementAndGetCrashNumber(): Int {
        return incrementAndGet(LAST_CRASH_NUMBER_KEY)
    }

    private companion object {
        private const val LAST_CRASH_NUMBER_KEY = "io.embrace.crashnumber"
    }
}
