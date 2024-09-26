package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import java.io.InputStream

/**
 * Stores a completed payload to disk. This service makes several assumptions around threading
 * that MUST be adhered to:
 *
 * 1. All calls to [store] are made from thread A
 * 2. All (external) calls to [delete] are executed on thread A (but can be submitted from
 * a different thread)
 * 3. The service may call [delete] internally on thread A to enforce storage limits
 * 4. [store] will be called exactly once when each payload is complete & ready to send. I.e. it
 * will never be called multiple times to persist an incomplete/transformed payload
 * 5. For a given payload, [delete] will always be called after [store]
 * 6. Callers to [loadPayloadAsStream] must be able to handle IOException when manipulating the
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
    fun delete(metadata: StoredTelemetryMetadata)

    /**
     * Loads a payload as an [InputStream]
     */
    fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream?
}
