package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import java.io.InputStream

/**
 * Stores arbitrary files in a directory.
 */
interface FileStorageService {

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
    fun getStoredPayloads(): List<StoredTelemetryMetadata>
}
