package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.utils.stream
import java.util.concurrent.CopyOnWriteArrayList

internal class EmbraceMemoryCleanerService(private val logger: EmbLogger) : MemoryCleanerService {

    /**
     * List of listeners that subscribe to clean services collections.
     */

    val listeners = CopyOnWriteArrayList<MemoryCleanerListener>()

    override fun cleanServicesCollections() {
        stream(listeners) { listener: MemoryCleanerListener ->
            try {
                listener.cleanCollections()
            } catch (ex: Exception) {
                logger.logWarning("Failed to clean collections on service listener", ex)
                logger.trackInternalError(InternalErrorType.MEMORY_CLEAN_LISTENER_FAIL, ex)
            }
        }
    }

    override fun addListener(listener: MemoryCleanerListener) {
        listeners.addIfAbsent(listener)
    }
}
