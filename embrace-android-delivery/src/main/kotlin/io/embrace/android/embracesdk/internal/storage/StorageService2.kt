package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService

/**
 * TODO: merge this into [PayloadStorageService]
 */
interface StorageService2 : PayloadStorageService {
    fun getPayloadsByPriority(): List<StoredTelemetryMetadata>
}
