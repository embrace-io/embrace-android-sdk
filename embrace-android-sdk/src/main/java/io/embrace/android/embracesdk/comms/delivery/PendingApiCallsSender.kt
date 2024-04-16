package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.utils.SerializationAction

/**
 * Manages the Pending API calls and schedules them to be sent later.
 */
internal interface PendingApiCallsSender {

    /**
     * Sets the method to be used when sending an [ApiRequest].
     */
    fun setSendMethod(sendMethod: (request: ApiRequest, action: SerializationAction) -> ApiResponse)

    /**
     * Save an API call to be sent later.
     */
    fun savePendingApiCall(request: ApiRequest, action: SerializationAction, sync: Boolean = false)

    /**
     * Schedules the retry of all pending API calls.
     */
    fun scheduleRetry(response: ApiResponse)
}
