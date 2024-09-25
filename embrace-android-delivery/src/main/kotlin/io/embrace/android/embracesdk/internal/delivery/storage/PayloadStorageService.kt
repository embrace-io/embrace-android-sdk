package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import java.io.InputStream

/**
 * Stores a completed payload to disk.
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
