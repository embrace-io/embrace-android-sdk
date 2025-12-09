package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.KeyValueStoreEditor
import java.util.concurrent.ConcurrentHashMap

class FakeKeyValueStore : KeyValueStore {

    private val map = mutableMapOf<String, Any?>()

    fun values(): Map<String, Any?> = map.toMap()

    override fun getString(key: String): String? {
        return map[key] as? String
    }

    override fun getInt(key: String): Int? {
        return map[key] as? Int
    }

    override fun getLong(key: String): Long? {
        return map[key] as? Long
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return map[key] as? Boolean ?: defaultValue
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String): Set<String>? {
        return map[key] as? Set<String>
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringMap(key: String): Map<String, String>? {
        return map[key] as? Map<String, String>
    }

    override fun edit(action: KeyValueStoreEditor.() -> Unit) {
        FakeEditor(map).action()
    }

    override fun incrementAndGet(key: String): Int {
        val newValue = (map[key] as? Int ?: 0) + 1
        map[key] = newValue
        return newValue
    }

    private class FakeEditor(private val map: MutableMap<String, Any?>) : KeyValueStoreEditor {
        override fun putString(key: String, value: String?) {
            map[key] = value
        }

        override fun putInt(key: String, value: Int?) {
            map[key] = value
        }

        override fun putLong(key: String, value: Long?) {
            map[key] = value
        }

        override fun putBoolean(key: String, value: Boolean?) {
            map[key] = value
        }

        override fun putStringSet(key: String, value: Set<String>?) {
            map[key] = value
        }

        override fun putStringMap(key: String, value: Map<String, String>?) {
            map[key] = value
        }

        override fun close() {
        }
    }
}
