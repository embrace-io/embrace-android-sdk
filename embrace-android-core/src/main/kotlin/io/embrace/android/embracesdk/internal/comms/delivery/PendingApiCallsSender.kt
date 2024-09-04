package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.injection.SerializationAction

/**
 * Manages the Pending API calls and schedules them to be sent later.
 */
public interface PendingApiCallsSender : NetworkConnectivityListener {

    /**
     * Initializes the retry scheduling with a method to be used when sending an [ApiRequest].
     */
    public fun initializeRetrySchedule(sendMethod: (request: ApiRequest, action: SerializationAction) -> ApiResponse)

    /**
     * Save an API call to be sent later.
     */
    public fun savePendingApiCall(request: ApiRequest, action: SerializationAction, sync: Boolean = false)

    /**
     * Schedules the retry of all pending API calls.
     */
    public fun scheduleRetry(response: ApiResponse)
}
