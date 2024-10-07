package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState

class FakePayloadCachingService : PayloadCachingService {

    override fun reportBackgroundActivityStateChange() {
    }

    override fun shutdown() {
    }

    override fun startCaching(state: ProcessState, supplier: () -> Envelope<SessionPayload>?) {
    }

    override fun stopCaching() {
    }
}
