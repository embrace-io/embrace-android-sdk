package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiResponse

/**
 * Manages the Pending API calls and schedules them to be sent later.
 */
internal interface PendingApiCallsSender {

    /**
     * Sets the method to be used when sending an [ApiRequest]
     */
    fun setSendMethod(sendMethod: (request: ApiRequest, payload: ByteArray) -> ApiResponse)

    /**
     * Determines if a failed API call should be retried.
     */
    fun shouldRetry(response: ApiResponse): Boolean

    /**
     * Saves an API call to be sent later and returns the corresponding [PendingApiCall].
     */
    fun savePendingApiCall(request: ApiRequest, payload: ByteArray): PendingApiCall

    /**
     * Schedules an API call to be sent later.
     */
    fun scheduleApiCall(response: ApiResponse)
}
