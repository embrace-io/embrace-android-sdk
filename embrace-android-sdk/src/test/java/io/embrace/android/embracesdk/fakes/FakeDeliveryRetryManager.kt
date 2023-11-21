package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.delivery.DeliveryRetryManager
import java.util.LinkedList
import java.util.Queue

internal class FakeDeliveryRetryManager : DeliveryRetryManager {

    val retryQueue: Queue<Pair<ApiRequest, ByteArray?>> = LinkedList()
    private var retryMethod: ((ApiRequest, ByteArray) -> Unit)? = null

    override fun scheduleForRetry(request: ApiRequest, payload: ByteArray) {
        check(retryMethod != null) { "Retried to schedule retry before component is ready" }
        retryQueue.add(Pair(request, payload))
    }

    override fun setRetryMethod(retryMethod: (request: ApiRequest, payload: ByteArray) -> Unit) {
        this.retryMethod = retryMethod
    }
}
