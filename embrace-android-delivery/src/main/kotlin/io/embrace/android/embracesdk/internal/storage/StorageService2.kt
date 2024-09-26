package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import java.io.InputStream

interface StorageService2 {
    fun getPayloadsByPriority(): List<StoredTelemetryMetadata>
    fun loadPayloadAsStream(payloadMetadata: StoredTelemetryMetadata): InputStream?
    fun deletePayload(payloadMetadata: StoredTelemetryMetadata)
}
