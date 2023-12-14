package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiResponse
import io.embrace.android.embracesdk.comms.delivery.PendingApiCall
import io.embrace.android.embracesdk.comms.delivery.PendingApiCallsSender
import java.util.LinkedList
import java.util.Queue

internal class FakePendingApiCallsSender : PendingApiCallsSender {

    val pendingApiCalls: Queue<PendingApiCall> = LinkedList()
    private var sendMethod: ((ApiRequest, ByteArray) -> ApiResponse)? = null

    override fun scheduleApiCall(response: ApiResponse) {
        check(sendMethod != null) { "Retried to schedule retry before component is ready" }
    }

    override fun setSendMethod(sendMethod: (request: ApiRequest, payload: ByteArray) -> ApiResponse) {
        this.sendMethod = sendMethod
    }

    override fun savePendingApiCall(request: ApiRequest, payload: ByteArray): PendingApiCall {
        val pendingApiCall = PendingApiCall(request, "payload_name")
        pendingApiCalls.add(pendingApiCall)
        return pendingApiCall
    }

    override fun removePendingApiCall(pendingApiCall: PendingApiCall) {
        if (pendingApiCalls.contains(pendingApiCall)) {
            pendingApiCalls.remove(pendingApiCall)
        }
    }

    override fun shouldRetry(response: ApiResponse): Boolean {
        return when (response) {
            is ApiResponse.Success,
            is ApiResponse.NotModified,
            is ApiResponse.PayloadTooLarge,
            is ApiResponse.Failure -> false

            is ApiResponse.TooManyRequests,
            is ApiResponse.Incomplete -> true
        }
    }
}
