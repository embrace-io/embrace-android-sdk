package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.caching.SessionPartSnapshotter
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.arch.state.ProcessState

class FakePayloadCachingService : PayloadCachingService {

    override fun reportBackgroundActivityStateChange() {
    }

    override fun shutdown() {
    }

    override fun startCaching(
        initial: SessionPartToken,
        state: ProcessState,
        snapshotter: SessionPartSnapshotter,
    ) {
    }

    override fun stopCaching() {
    }
}
