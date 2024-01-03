package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.logging.InternalErrorService

internal interface MemoryCleanerService {

    /**
     * Adds an observer of the end session event.
     *
     * @param listener the observer to register
     */
    fun addListener(listener: MemoryCleanerListener)

    /**
     * Flush collections from each service which has collections in memory.
     */
    fun cleanServicesCollections(
        internalErrorService: InternalErrorService
    )
}
