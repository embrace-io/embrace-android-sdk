package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.injection.SerializationAction

/**
 * Implementation used when the v2 delivery layer is enabled
 */
internal class NoopPendingApiCallSender : PendingApiCallsSender {
    override fun initializeRetrySchedule(
        sendMethod: (request: ApiRequest, action: SerializationAction) -> ApiResponse
    ) { }

    override fun savePendingApiCall(request: ApiRequest, action: SerializationAction, sync: Boolean) { }

    override fun scheduleRetry(response: ApiResponse) { }

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) { }
}
