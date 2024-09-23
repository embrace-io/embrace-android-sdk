package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.payload.Envelope

class PayloadCachingServiceImpl : PayloadCachingService {

    override fun handleCrash(crashId: String) {
    }

    override fun <T> startCaching(
        envelopeProvider: () -> Envelope<T>,
        metadataProvider: () -> StoredTelemetryMetadata
    ): String {
        return ""
    }

    override fun stopCaching(id: String) {
    }
}
