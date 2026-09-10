package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
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
     * The number of bytes the payload occupies on disk, or 0 if it is not stored or its size
     * could not be read.
     */
    fun payloadSizeBytes(metadata: StoredTelemetryMetadata): Long

    /**
     * Return stored payloads as a list sorted in priority order
     */
    fun getStoredPayloads(): List<StoredTelemetryMetadata>
}
