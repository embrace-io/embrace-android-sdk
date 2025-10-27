package io.embrace.android.embracesdk.internal.prefs

import android.content.SharedPreferences
import io.embrace.android.embracesdk.internal.arch.store.KeyValueStoreEditor
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer

internal class SharedPrefsStoreEditor(
    private val editor: SharedPreferences.Editor,
    private val serializer: PlatformSerializer,
) : KeyValueStoreEditor, AutoCloseable {

    override fun putString(key: String, value: String?) {
        editor.putString(key, value)
    }

    override fun putInt(key: String, value: Int?) {
        editor.putInt(key, value ?: -1)
    }

    override fun putLong(key: String, value: Long?) {
        editor.putLong(key, value ?: -1L)
    }

    override fun putBoolean(key: String, value: Boolean?) {
        editor.putBoolean(key, value ?: false)
    }

    override fun putStringSet(key: String, value: Set<String>?) {
        editor.putStringSet(key, value)
    }

    override fun putStringMap(
        key: String,
        value: Map<String, String>?,
    ) {
        val mapString = when {
            value != null -> serializer.toJson(value, Map::class.java)
            else -> null
        }
        editor.putString(key, mapString)
    }

    override fun close() = editor.apply()
}
