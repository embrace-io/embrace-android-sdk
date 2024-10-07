package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState

class NoopPayloadCachingService : PayloadCachingService {

    override fun startCaching(state: ProcessState, supplier: () -> Envelope<SessionPayload>?) {
    }

    override fun stopCaching() {
    }

    override fun reportBackgroundActivityStateChange() {
    }

    override fun shutdown() {
    }
}
