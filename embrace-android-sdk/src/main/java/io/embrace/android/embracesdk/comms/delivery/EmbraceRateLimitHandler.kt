package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint

internal class EmbraceRateLimitHandler : RateLimitHandler {

    private val rateLimitMap = mutableMapOf<Endpoint, RateLimit>()

    override fun setRateLimit(endpoint: Endpoint, retryAfter: Long?) {
        val retries = rateLimitMap[endpoint]?.retries?.plus(1) ?: 1
        rateLimitMap[endpoint] = RateLimit(retries, retryAfter ?: calculateRetryAfter(endpoint))
    }

    override fun isRateLimited(endpoint: Endpoint): Boolean {
        return rateLimitMap.containsKey(endpoint)
    }

    override fun clearRateLimit(endpoint: Endpoint) {
        rateLimitMap.remove(endpoint)
    }

    override fun getInitialDelay(retryAfter: Long?): Long {
        return retryAfter ?: INITIAL_RETRY_AFTER_IN_SECONDS
    }

    private fun calculateRetryAfter(endpoint: Endpoint): Long {
        return rateLimitMap[endpoint]?.let {
            it.retryAfter * 2
        } ?: INITIAL_RETRY_AFTER_IN_SECONDS
    }
}

private const val INITIAL_RETRY_AFTER_IN_SECONDS = 3L
