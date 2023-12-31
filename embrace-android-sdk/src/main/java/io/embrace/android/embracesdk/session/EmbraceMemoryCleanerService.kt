package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDebug
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.utils.stream
import java.util.concurrent.CopyOnWriteArrayList

internal class EmbraceMemoryCleanerService : MemoryCleanerService {

    /**
     * List of listeners that subscribe to clean services collections.
     */

    val listeners = CopyOnWriteArrayList<MemoryCleanerListener>()

    override fun cleanServicesCollections(
        internalErrorService: InternalErrorService
    ) {
        logDeveloper("EmbraceMemoryCleanerService", "Clean services collections")

        stream(listeners) { listener: MemoryCleanerListener ->
            try {
                listener.cleanCollections()
            } catch (ex: Exception) {
                logDebug("Failed to clean collections on service listener", ex)
            }
        }
        internalErrorService.resetExceptionErrorObject()
    }

    override fun addListener(listener: MemoryCleanerListener) {
        listeners.addIfAbsent(listener)
    }
}
