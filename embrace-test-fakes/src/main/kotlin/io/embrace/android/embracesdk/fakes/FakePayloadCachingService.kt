package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload

class FakePayloadCachingService : PayloadCachingService {

    override fun shutdown() {
    }

    override fun startCaching(isInBackground: Boolean, supplier: () -> Envelope<SessionPayload>?) {
    }

    override fun stopCaching() {
    }
}
