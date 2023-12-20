package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.SerializationAction
import io.embrace.android.embracesdk.comms.delivery.PendingApiCallsSender
import java.io.ByteArrayOutputStream
import java.util.LinkedList
import java.util.Queue

internal class FakePendingApiCallsSender : PendingApiCallsSender {

    val retryQueue: Queue<Pair<ApiRequest, ByteArray?>> = LinkedList()
    private var sendMethod: ((ApiRequest, SerializationAction) -> Unit)? = null

    override fun scheduleApiCall(request: ApiRequest, action: SerializationAction) {
        check(sendMethod != null) { "Retried to schedule retry before component is ready" }
        val stream = ByteArrayOutputStream()
        action(stream)
        retryQueue.add(Pair(request, stream.toByteArray()))
    }

    override fun setSendMethod(sendMethod: (request: ApiRequest, action: SerializationAction) -> Unit) {
        this.sendMethod = sendMethod
    }
}
