package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiResponse

/**
 * Manages the Pending API calls and schedules them to be sent later.
 */
internal interface PendingApiCallsSender {

    /**
     * Sets the method to be used when sending an [ApiRequest].
     */
    fun setSendMethod(sendMethod: (request: ApiRequest, payload: ByteArray) -> ApiResponse)

    /**
     * Saves an API call to be sent later and returns the corresponding [PendingApiCall].
     */
    fun savePendingApiCall(request: ApiRequest, payload: ByteArray): PendingApiCall

    /**
     * Schedules the retry of all pending API calls.
     */
    fun scheduleRetry(response: ApiResponse)
}
