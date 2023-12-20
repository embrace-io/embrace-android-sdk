package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.SerializationAction

/**
 * Manages the Pending API calls and schedules them to be sent later.
 */
internal interface PendingApiCallsSender {

    /**
     * Sets the method to be used when sending an [ApiRequest]
     */
    fun setSendMethod(sendMethod: (request: ApiRequest, action: SerializationAction) -> Unit)

    /**
     * Schedules an API call to be sent later.
     */
    fun scheduleApiCall(request: ApiRequest, action: SerializationAction)
}
