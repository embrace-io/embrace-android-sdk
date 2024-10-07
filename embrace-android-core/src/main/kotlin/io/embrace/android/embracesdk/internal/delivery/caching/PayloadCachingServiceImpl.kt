package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.caching.PeriodicBackgroundActivityCacher
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionCacher

class PayloadCachingServiceImpl(
    private val periodicSessionCacher: PeriodicSessionCacher,
    private val periodicBackgroundActivityCacher: PeriodicBackgroundActivityCacher
) : PayloadCachingService {

    override fun shutdown() {
    }

    override fun startCaching(isInBackground: Boolean, supplier: () -> Envelope<SessionPayload>?) {
        if (isInBackground) {
            periodicBackgroundActivityCacher.scheduleSave(supplier)
        } else {
            periodicSessionCacher.start(supplier)
        }
    }

    override fun stopCaching() {
        periodicSessionCacher.stop()
        periodicBackgroundActivityCacher.stop()
    }
}
