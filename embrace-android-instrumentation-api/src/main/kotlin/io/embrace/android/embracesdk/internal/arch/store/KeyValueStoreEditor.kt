package io.embrace.android.embracesdk.internal.arch.store

/**
 * Edits values in a key-value store.
 */
interface KeyValueStoreEditor : AutoCloseable {

    /**
     * Associates a string value with the given key.
     */
    fun putString(key: String, value: String?)

    /**
     * Associates an int value with the given key.
     */
    fun putInt(key: String, value: Int?)

    /**
     * Associates a long value with the given key.
     */
    fun putLong(key: String, value: Long?)

    /**
     * Associates a boolean value with the given key.
     */
    fun putBoolean(key: String, value: Boolean?)

    /**
     * Associates a set of strings with the given key.
     */
    fun putStringSet(key: String, value: Set<String>?)

    /**
     * Associates a Map of string key-value pairs with the given key.
     */
    fun putStringMap(key: String, value: Map<String, String>?)
}
