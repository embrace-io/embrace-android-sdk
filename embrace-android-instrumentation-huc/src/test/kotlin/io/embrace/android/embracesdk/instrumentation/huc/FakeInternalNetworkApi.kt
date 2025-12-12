package io.embrace.android.embracesdk.instrumentation.huc

import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal class FakeInternalNetworkApi(
    var time: Long = 0,
    var captureNetworkBody: Boolean = true,
    var networkSpanForwardingEnabled: Boolean = false,
) : InternalNetworkApi {

    val networkRequests: MutableList<EmbraceNetworkRequest> = mutableListOf()

    override fun getSdkCurrentTimeMs(): Long = time
    override fun isNetworkSpanForwardingEnabled(): Boolean = networkSpanForwardingEnabled
    override fun recordNetworkRequest(embraceNetworkRequest: EmbraceNetworkRequest) {
        networkRequests.add(embraceNetworkRequest)
    }

    override fun shouldCaptureNetworkBody(
        url: String,
        method: String,
    ): Boolean = captureNetworkBody

    override fun logInternalError(error: Throwable) {}
}
