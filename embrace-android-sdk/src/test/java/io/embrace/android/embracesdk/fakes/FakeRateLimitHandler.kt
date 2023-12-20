package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.Endpoint
import io.embrace.android.embracesdk.comms.delivery.RateLimitHandler

internal class FakeRateLimitHandler : RateLimitHandler {

    var didScheduledRetry = false

    override fun scheduleRetry(
        endpoint: Endpoint,
        retryAfter: Long?,
        retryMethod: () -> Unit
    ) {
        didScheduledRetry = true
        retryMethod()
    }
}
