package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import java.io.InputStream

/**
 * Stores a completed payload to disk. This service makes several assumptions around threading
 * that MUST be adhered to:
 *
 * 1. All calls to [store] are made from thread A
 * 2. All external calls to [delete] can be made from any thread but will be enqueued on thread A
 * 3. The service may delete files internally on thread A to enforce storage limits
 * 4. For any given payload [store] will always be called before [delete]
 * 5. Callers to [loadPayloadAsStream] must be able to handle IOException when manipulating the
 * stream as the payload file backing the stream could be deleted at any time
 */
interface PayloadStorageService {

    /**
     * Stores a payload
     */
    fun store(metadata: StoredTelemetryMetadata, action: SerializationAction)

    /**
     * Deletes a payload
     */
    fun delete(metadata: StoredTelemetryMetadata, callback: () -> Unit = {})

    /**
     * Loads a payload as an [InputStream]
     */
    fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream?

    /**
     * Return stored payloads as a list sorted in priority order
     */
    fun getPayloadsByPriority(): List<StoredTelemetryMetadata>

    /**
     * Return cached payloads from previous app instances
     */
    fun getUndeliveredPayloads(): List<StoredTelemetryMetadata>
}
