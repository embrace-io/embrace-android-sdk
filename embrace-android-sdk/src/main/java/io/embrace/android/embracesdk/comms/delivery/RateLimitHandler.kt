package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint

internal interface RateLimitHandler {

    /**
     * Sets the rate limit for the given endpoint and schedules a task to execute the api calls ofter
     * the given retry after time or the exponential backoff delay calculated from the number of retries.
     */
    fun setRateLimitAndScheduleRetry(
        endpoint: Endpoint,
        retryAfter: Long?,
        retryMethod: () -> Unit
    )

    /**
     * Returns true if the given endpoint is rate limited.
     */
    fun isRateLimited(endpoint: Endpoint): Boolean

    /**
     * Clears the rate limit for the given endpoint.
     */
    fun clearRateLimit(endpoint: Endpoint)
}
