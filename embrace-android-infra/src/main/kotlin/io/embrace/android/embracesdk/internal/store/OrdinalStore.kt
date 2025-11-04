package io.embrace.android.embracesdk.internal.store

interface OrdinalStore {

    /**
     * Increments and returns the ordinal.
     */
    fun incrementAndGet(ordinal: Ordinal): Int
}
