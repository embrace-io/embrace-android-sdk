package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import java.io.InputStream

/**
 * Stores arbitrary files in a directory.
 */
interface FileStorageService {

    /**
     * Loads a stored payload as an [InputStream]
     */
    fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream?
}
