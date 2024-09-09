package io.embrace.android.embracesdk.internal.session

interface MemoryCleanerService {

    /**
     * Adds an observer of the end session event.
     *
     * @param listener the observer to register
     */
    fun addListener(listener: MemoryCleanerListener)

    /**
     * Flush collections from each service which has collections in memory.
     */
    fun cleanServicesCollections()
}
