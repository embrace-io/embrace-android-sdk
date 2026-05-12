package io.embrace.android.embracesdk.internal.store

interface OrdinalStore {

    /**
     * Increments and returns the ordinal. An ordinal that has never been written returns the value
     * supplied by [seed] on its first read; subsequent calls increment by 1. [seed] returns 1 by default.
     */
    fun incrementAndGet(ordinal: Ordinal, seed: () -> Int = { 1 }): Int
}
