package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.delivery.DeliveryRetryManager

internal class FakeDeliveryRetryManager : DeliveryRetryManager {
    override fun scheduleForRetry(request: ApiRequest, payload: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun setPostExecutor(postExecutor: (request: ApiRequest, payload: ByteArray) -> Unit) {
        TODO("Not yet implemented")
    }
}
