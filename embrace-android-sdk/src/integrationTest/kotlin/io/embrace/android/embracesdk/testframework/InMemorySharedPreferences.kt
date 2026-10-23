package io.embrace.android.embracesdk.testframework

import android.content.Context
import android.content.SharedPreferences
import java.util.WeakHashMap

/**
 * A thread-safe [SharedPreferences] held in memory. Robolectric's real implementation writes the whole file to disk
 * and fsyncs it on every commit.
 */
internal class InMemorySharedPreferences : SharedPreferences {

    private val lock = Any()
    private val values = HashMap<String, Any>()

    override fun getAll(): Map<String, *> = synchronized(lock) { HashMap(values) }

    override fun getString(key: String, defValue: String?): String? = read(key, defValue)

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = read(key, defValues)

    override fun getInt(key: String, defValue: Int): Int = read(key, defValue)

    override fun getLong(key: String, defValue: Long): Long = read(key, defValue)

    override fun getFloat(key: String, defValue: Float): Float = read(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean = read(key, defValue)

    override fun contains(key: String): Boolean = synchronized(lock) { values.containsKey(key) }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        throw UnsupportedOperationException()
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        throw UnsupportedOperationException()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> read(key: String, defValue: T): T = synchronized(lock) { values[key] as? T ?: defValue }

    /**
     * Each editor is used by one thread, as the SDK only ever creates one per commit.
     */
    private inner class Editor : SharedPreferences.Editor {

        private val changes = LinkedHashMap<String, Any?>()

        override fun putString(key: String, value: String?) = put(key, value)

        override fun putStringSet(key: String, values: Set<String>?) = put(key, values?.toHashSet())

        override fun putInt(key: String, value: Int) = put(key, value)

        override fun putLong(key: String, value: Long) = put(key, value)

        override fun putFloat(key: String, value: Float) = put(key, value)

        override fun putBoolean(key: String, value: Boolean) = put(key, value)

        override fun remove(key: String) = put(key, null)

        override fun clear(): SharedPreferences.Editor = throw UnsupportedOperationException()

        override fun commit(): Boolean {
            synchronized(lock) {
                changes.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
            }
            changes.clear()
            return true
        }

        override fun apply() {
            commit()
        }

        private fun put(key: String, value: Any?) = apply { changes[key] = value }
    }

    companion object {
        private val byApplication = WeakHashMap<Context, MutableMap<String, InMemorySharedPreferences>>()

        fun get(context: Context, name: String): InMemorySharedPreferences = synchronized(byApplication) {
            byApplication.getOrPut(context.applicationContext, ::HashMap).getOrPut(name, ::InMemorySharedPreferences)
        }
    }
}
