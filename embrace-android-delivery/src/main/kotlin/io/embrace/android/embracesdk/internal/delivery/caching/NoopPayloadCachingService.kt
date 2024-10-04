package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload

class NoopPayloadCachingService : PayloadCachingService {
    override fun shutdown() {
    }

    override fun startCaching(isInBackground: Boolean, supplier: () -> Envelope<SessionPayload>?) {
    }

    override fun stopCaching() {
    }
}
