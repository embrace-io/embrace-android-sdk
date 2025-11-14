package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.caching.SessionPayloadSupplier
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.arch.state.AppState

class FakePayloadCachingService : PayloadCachingService {

    override fun reportBackgroundActivityStateChange() {
    }

    override fun shutdown() {
    }

    override fun startCaching(
        initial: SessionZygote,
        state: AppState,
        supplier: SessionPayloadSupplier,
    ) {
    }

    override fun stopCaching() {
    }
}
