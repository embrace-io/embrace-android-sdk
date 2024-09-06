package io.embrace.android.embracesdk.internal.session

interface MemoryCleanerListener {

    /**
     * Clean collections in memory when a session ends occurs.
     */
    fun cleanCollections()
}
