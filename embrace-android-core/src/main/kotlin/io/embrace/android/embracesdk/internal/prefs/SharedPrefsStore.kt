@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.prefs

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.KeyValueStoreEditor
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Creates the SDK's [KeyValueStore], backed by the default [SharedPreferences].
 *
 * Loading the underlying preferences blocks on disk, so callers should hold this behind a [Lazy] and
 * share that one instance rather than resolving it eagerly.
 */
fun createKeyValueStore(context: Context, serializer: PlatformSerializer): KeyValueStore =
    SharedPrefsStore(PreferenceManager.getDefaultSharedPreferences(context), serializer)

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

    override fun getStringMap(key: String): Map<String, String>? {
        val mapString = impl.getString(key, null) ?: return null
        return serializer.fromJson(mapString, mapSerializer)
    }

    override fun edit(action: KeyValueStoreEditor.() -> Unit) {
        SharedPrefsStoreEditor(impl.edit(), serializer).use {
            it.action()
        }
    }

    private companion object {
        val mapSerializer = MapSerializer(String.serializer(), String.serializer())
    }
}
