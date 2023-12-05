package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint
import io.embrace.android.embracesdk.comms.delivery.RateLimitHandler

internal class FakeRateLimitHandler : RateLimitHandler {
    override fun setRateLimit(endpoint: Endpoint, retryAfter: Long?) {
        TODO("Not yet implemented")
    }

    override fun isRateLimited(endpoint: Endpoint): Boolean {
        return false
    }

    override fun clearRateLimit(endpoint: Endpoint) {
        TODO("Not yet implemented")
    }

    override fun getInitialDelay(retryAfter: Long?): Long {
        return retryAfter ?: INITIAL_RETRY_AFTER_IN_SECONDS
    }
}

private const val INITIAL_RETRY_AFTER_IN_SECONDS = 3L
