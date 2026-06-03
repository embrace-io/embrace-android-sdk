package io.embrace.android.embracesdk.internal.prefs

import android.content.SharedPreferences
import android.util.JsonReader
import android.util.JsonToken
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.KeyValueStoreEditor
import java.io.StringReader

internal class SharedPrefsStore(
    private val impl: SharedPreferences,
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

    override fun getStringMap(key: String): Map<String, String>? {
        val mapString = impl.getString(key, null) ?: return null
        return try {
            JsonReader(StringReader(mapString)).use { reader ->
                val map = LinkedHashMap<String, String>()
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        map[name] = reader.nextString()
                    }
                }
                reader.endObject()
                map
            }
        } catch (exc: Exception) {
            null
        }
    }

    override fun edit(action: KeyValueStoreEditor.() -> Unit) {
        SharedPrefsStoreEditor(impl.edit()).use {
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
}
