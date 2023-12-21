package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiResponse
import io.embrace.android.embracesdk.comms.delivery.PendingApiCall
import io.embrace.android.embracesdk.comms.api.SerializationAction
import io.embrace.android.embracesdk.comms.delivery.PendingApiCallsSender
import java.io.ByteArrayOutputStream
import java.util.LinkedList
import java.util.Queue

internal class FakePendingApiCallsSender : PendingApiCallsSender {

    var didScheduleApiCall = false
    val retryQueue: Queue<Pair<ApiRequest, ByteArray?>> = LinkedList()
    private var sendMethod: ((ApiRequest, SerializationAction) -> ApiResponse)? = null

    override fun scheduleRetry(response: ApiResponse) {
        check(sendMethod != null) { "Retried to schedule retry before component is ready" }
        didScheduleApiCall = true
    }

    override fun setSendMethod(sendMethod: (request: ApiRequest, action: SerializationAction) -> ApiResponse) {
        this.sendMethod = sendMethod
    }

    override fun savePendingApiCall(request: ApiRequest, action: SerializationAction): PendingApiCall {
        val stream = ByteArrayOutputStream()
        action(stream)
        retryQueue.add(Pair(request, stream.toByteArray()))

    }
}
