package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.utils.stream
import java.util.concurrent.CopyOnWriteArrayList

internal class EmbraceMemoryCleanerService(private val logger: EmbLogger) : MemoryCleanerService {

    /**
     * List of listeners that subscribe to clean services collections.
     */

    val listeners: CopyOnWriteArrayList<MemoryCleanerListener> = CopyOnWriteArrayList<MemoryCleanerListener>()

    override fun cleanServicesCollections() {
        stream(listeners) { listener: MemoryCleanerListener ->
            try {
                listener.cleanCollections()
            } catch (ex: Exception) {
                logger.trackInternalError(InternalErrorType.MEMORY_CLEAN_LISTENER_FAIL, ex)
            }
        }
    }

    override fun addListener(listener: MemoryCleanerListener) {
        listeners.addIfAbsent(listener)
    }
}
