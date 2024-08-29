package io.embrace.android.embracesdk.internal.session

public interface MemoryCleanerService {

    /**
     * Adds an observer of the end session event.
     *
     * @param listener the observer to register
     */
    public fun addListener(listener: MemoryCleanerListener)

    /**
     * Flush collections from each service which has collections in memory.
     */
    public fun cleanServicesCollections()
}
