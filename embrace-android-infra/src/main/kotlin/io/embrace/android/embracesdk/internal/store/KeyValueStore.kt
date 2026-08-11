package io.embrace.android.embracesdk.internal.store

/**
 * Retrieves values from a key-value store.
 */
interface KeyValueStore {

    /**
     * Retrieves a string from the key-value store that matches the given key.
     */
    fun getString(key: String): String?

    /**
     * Retrieves an int from the key-value store that matches the given key.
     */
    fun getInt(key: String): Int?

    /**
     * Retrieves a long from the key-value store that matches the given key.
     */
    fun getLong(key: String): Long?

    /**
     * Retrieves a boolean from the key-value store that matches the given key.
     */
    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    /**
     * Retrieves a set of strings from the key-value store that matches the given key.
     */
    fun getStringSet(key: String): Set<String>?

    /**
     * Retrieves a Map of string key-value pairs from the key-value store that matches the given key.
     */
    fun getStringMap(key: String): Map<String, String>?

    /**
     * Edits values in the key-value store, then commits them.
     *
     * Each call costs its own commit, and therefore its own disk write, so only reach for this
     * directly when writing a single key. Wrap multiple writes in [batch] so they share one commit.
     */
    fun editAndCommit(action: KeyValueStoreEditor.() -> Unit)

    /**
     * Coalesces every [editAndCommit] performed on the calling thread inside [action] into a single commit,
     * which is issued when the outermost [batch] returns. As with [editAndCommit], the commit happens even if
     * [action] throws.
     */
    fun batch(action: () -> Unit) = action()
}
