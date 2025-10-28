package io.embrace.android.embracesdk.internal.arch.store

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
     * Performs a batch edit of values in the key-value store.
     */
    fun edit(action: KeyValueStoreEditor.() -> Unit)

    /**
     * Increments an int from the key-value store then returns it.
     */
    fun incrementAndGet(key: String): Int

    /**
     * Increments and returns the crash number ordinal. This is an integer that
     * increments on every crash. It allows us to check the % of crashes that
     * didn't get delivered to the backend.
     */
    fun incrementAndGetCrashNumber(): Int
}
