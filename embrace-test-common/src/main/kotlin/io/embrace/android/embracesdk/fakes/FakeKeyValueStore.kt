package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.KeyValueStoreEditor

class FakeKeyValueStore : KeyValueStore {

    private val map = mutableMapOf<String, Any?>()

    /**
     * Writes buffered by an open [batch]. Reads consult this first so the fake models the
     * read-your-writes behaviour of the real store.
     */
    private var openBatch: MutableMap<String, Any?>? = null

    /**
     * The number of commits that a real store would have performed. Every [editAndCommit] outside a [batch]
     * counts as one, and a [batch] counts as one no matter how many [editAndCommit] calls it contains.
     */
    var commitCount: Int = 0
        private set

    fun values(): Map<String, Any?> = map.toMap()

    override fun getString(key: String): String? {
        return read(key) as? String
    }

    override fun getInt(key: String): Int? {
        return read(key) as? Int
    }

    override fun getLong(key: String): Long? {
        return read(key) as? Long
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return read(key) as? Boolean ?: defaultValue
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String): Set<String>? {
        return read(key) as? Set<String>
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringMap(key: String): Map<String, String>? {
        return read(key) as? Map<String, String>
    }

    override fun editAndCommit(action: KeyValueStoreEditor.() -> Unit) {
        val batch = openBatch
        if (batch != null) {
            FakeEditor(batch).action()
        } else {
            commitCount++
            FakeEditor(map).action()
        }
    }

    override fun batch(action: () -> Unit) {
        if (openBatch != null) { // a batch is already open, so it owns the commit
            action()
            return
        }
        val batch = mutableMapOf<String, Any?>()
        openBatch = batch
        try {
            action()
        } finally {
            openBatch = null
            if (batch.isNotEmpty()) {
                commitCount++
                map.putAll(batch)
            }
        }
    }

    private fun read(key: String): Any? {
        val batch = openBatch ?: return map[key]
        return if (batch.containsKey(key)) batch[key] else map[key]
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
