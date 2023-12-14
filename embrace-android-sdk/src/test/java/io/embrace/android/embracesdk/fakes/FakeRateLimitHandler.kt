package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint
import io.embrace.android.embracesdk.comms.delivery.RateLimitHandler

internal class FakeRateLimitHandler : RateLimitHandler {

    private val rateLimitMap = mutableMapOf<Endpoint, Int>()

    override fun setRateLimitAndScheduleRetry(
        endpoint: Endpoint,
        retryAfter: Long?,
        retryMethod: () -> Unit
    ) {
        rateLimitMap[endpoint] = rateLimitMap[endpoint]?.plus(1) ?: 1
    }

    override fun isRateLimited(endpoint: Endpoint): Boolean {
        return rateLimitMap.containsKey(endpoint)
    }

    override fun clearRateLimit(endpoint: Endpoint) {
        rateLimitMap.remove(endpoint)
    }
}
