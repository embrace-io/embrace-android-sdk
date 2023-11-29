package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.delivery.PendingApiCallsSender
import java.util.LinkedList
import java.util.Queue

internal class FakePendingApiCallsSender : PendingApiCallsSender {

    val retryQueue: Queue<Pair<ApiRequest, ByteArray?>> = LinkedList()
    private var sendMethod: ((ApiRequest, ByteArray) -> Unit)? = null

    override fun scheduleApiCall(request: ApiRequest, payload: ByteArray) {
        check(sendMethod != null) { "Retried to schedule retry before component is ready" }
        retryQueue.add(Pair(request, payload))
    }

    override fun setSendMethod(sendMethod: (request: ApiRequest, payload: ByteArray) -> Unit) {
        this.sendMethod = sendMethod
    }
}
