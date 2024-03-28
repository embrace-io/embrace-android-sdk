package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.utils.stream
import java.util.concurrent.CopyOnWriteArrayList

internal class EmbraceMemoryCleanerService(private val logger: InternalEmbraceLogger) : MemoryCleanerService {

    /**
     * List of listeners that subscribe to clean services collections.
     */

    val listeners = CopyOnWriteArrayList<MemoryCleanerListener>()

    override fun cleanServicesCollections(
        internalErrorService: InternalErrorService
    ) {
        stream(listeners) { listener: MemoryCleanerListener ->
            try {
                listener.cleanCollections()
            } catch (ex: Exception) {
                logger.logDebug("Failed to clean collections on service listener", ex)
            }
        }
        internalErrorService.resetExceptionErrorObject()
    }

    override fun addListener(listener: MemoryCleanerListener) {
        listeners.addIfAbsent(listener)
    }
}
