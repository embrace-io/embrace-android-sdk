package io.embrace.android.embracesdk.session

internal interface MemoryCleanerListener {

    /**
     * Clean collections in memory when a session ends occurs.
     */
    fun cleanCollections()
}
