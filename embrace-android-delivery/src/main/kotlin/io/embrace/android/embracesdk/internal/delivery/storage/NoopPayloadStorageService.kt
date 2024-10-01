package io.embrace.android.embracesdk.internal.delivery.storage

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import java.io.InputStream

class NoopPayloadStorageService : PayloadStorageService {
    override fun store(metadata: StoredTelemetryMetadata, action: SerializationAction) {
    }

    override fun delete(metadata: StoredTelemetryMetadata) {
    }

    override fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream? {
        return null
    }

    override fun getPayloadsByPriority(): List<StoredTelemetryMetadata> {
        return emptyList()
    }
}
