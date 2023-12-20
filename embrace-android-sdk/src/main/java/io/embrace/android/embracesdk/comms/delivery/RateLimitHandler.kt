package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.Endpoint

internal interface RateLimitHandler {

    /**
     * Schedules a task to execute the api calls ofter the given retry after time or
     * the exponential backoff delay calculated from the number of retries.
     */
    fun scheduleRetry(
        endpoint: Endpoint,
        retryAfter: Long?,
        retryMethod: () -> Unit
    )
}
