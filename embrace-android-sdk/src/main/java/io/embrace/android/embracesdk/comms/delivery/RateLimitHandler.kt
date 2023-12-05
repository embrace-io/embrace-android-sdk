package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint

internal interface RateLimitHandler {

    /**
     * Sets the rate limit for the given endpoint.
     */
    fun setRateLimit(endpoint: Endpoint, retryAfter: Long? = null)

    /**
     * Returns true if the given endpoint is rate limited.
     */
    fun isRateLimited(endpoint: Endpoint): Boolean

    /**
     * Clears the rate limit for the given endpoint.
     */
    fun clearRateLimit(endpoint: Endpoint)

    /**
     * Returns the initial delay for the given retry after value.
     */
    fun getInitialDelay(retryAfter: Long?): Long
}
