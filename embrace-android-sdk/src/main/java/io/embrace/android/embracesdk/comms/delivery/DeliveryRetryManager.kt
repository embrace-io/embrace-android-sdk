package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.ApiRequest

/**
 * Manages the retrying of failed API calls.
 */
internal interface DeliveryRetryManager {

    /**
     * Schedules a failed API call for retry.
     */
    fun scheduleForRetry(request: ApiRequest, payload: ByteArray)

    /**
     * Sets the method to run to retry an [ApiRequest]
     */
    fun setRetryMethod(retryMethod: (request: ApiRequest, payload: ByteArray) -> Unit)
}
