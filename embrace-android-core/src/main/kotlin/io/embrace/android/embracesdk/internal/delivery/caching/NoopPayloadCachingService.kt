package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.payload.SessionZygote
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState

class NoopPayloadCachingService : PayloadCachingService {

    override fun startCaching(
        initial: SessionZygote,
        state: ProcessState,
        supplier: SessionPayloadSupplier,
    ) {
    }

    override fun stopCaching() {
    }

    override fun reportBackgroundActivityStateChange() {
    }

    override fun shutdown() {
    }
}
