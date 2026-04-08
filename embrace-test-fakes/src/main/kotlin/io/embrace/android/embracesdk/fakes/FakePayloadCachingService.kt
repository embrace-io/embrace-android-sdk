package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.caching.SessionPartPayloadSupplier
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.arch.state.AppState

class FakePayloadCachingService : PayloadCachingService {

    override fun reportBackgroundActivityStateChange() {
    }

    override fun shutdown() {
    }

    override fun startCaching(
        initial: SessionPartToken,
        state: AppState,
        supplier: SessionPartPayloadSupplier,
    ) {
    }

    override fun stopCaching() {
    }
}
