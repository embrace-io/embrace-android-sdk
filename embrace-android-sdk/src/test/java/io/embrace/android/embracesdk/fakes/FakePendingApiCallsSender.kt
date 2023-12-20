package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiResponse
import io.embrace.android.embracesdk.comms.delivery.PendingApiCall
import io.embrace.android.embracesdk.comms.delivery.PendingApiCallsSender

internal class FakePendingApiCallsSender : PendingApiCallsSender {

    val pendingApiCalls = mutableListOf<PendingApiCall>()
    var didScheduleApiCall = false
    private var sendMethod: ((ApiRequest, ByteArray) -> ApiResponse)? = null

    override fun scheduleRetry(response: ApiResponse) {
        check(sendMethod != null) { "Retried to schedule retry before component is ready" }
        didScheduleApiCall = true
    }

    override fun setSendMethod(sendMethod: (request: ApiRequest, payload: ByteArray) -> ApiResponse) {
        this.sendMethod = sendMethod
    }

    override fun savePendingApiCall(request: ApiRequest, payload: ByteArray): PendingApiCall {
        val pendingApiCall = PendingApiCall(request, "payload_name")
        pendingApiCalls.add(pendingApiCall)
        return pendingApiCall
    }
}
