package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionCacher

class PayloadCachingServiceImpl(
    private val periodicSessionCacher: PeriodicSessionCacher
) : PayloadCachingService {

    override fun shutdown() {
        periodicSessionCacher.shutdownAndWait()
    }

    override fun startCaching(supplier: () -> Envelope<SessionPayload>?) {
        periodicSessionCacher.start(supplier)
    }

    override fun stopCaching() {
        periodicSessionCacher.stop()
    }
}
