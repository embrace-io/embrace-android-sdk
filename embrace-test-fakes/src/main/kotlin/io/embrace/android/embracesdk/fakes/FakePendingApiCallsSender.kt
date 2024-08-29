package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.comms.delivery.PendingApiCallsSender
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import java.io.ByteArrayOutputStream
import java.util.LinkedList
import java.util.Queue

public class FakePendingApiCallsSender : PendingApiCallsSender {

    public var didScheduleApiCall: Boolean = false
    public val retryQueue: Queue<Pair<ApiRequest, ByteArray?>> = LinkedList()
    private var sendMethod: ((ApiRequest, SerializationAction) -> ApiResponse)? = null

    override fun scheduleRetry(response: ApiResponse) {
        check(sendMethod != null) { "Retried to schedule retry before component is ready" }
        didScheduleApiCall = true
    }

    override fun setSendMethod(sendMethod: (request: ApiRequest, action: SerializationAction) -> ApiResponse) {
        this.sendMethod = sendMethod
    }

    override fun savePendingApiCall(request: ApiRequest, action: SerializationAction, sync: Boolean) {
        val stream = ByteArrayOutputStream()
        action(stream)
        retryQueue.add(Pair(request, stream.toByteArray()))
    }

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
    }
}
