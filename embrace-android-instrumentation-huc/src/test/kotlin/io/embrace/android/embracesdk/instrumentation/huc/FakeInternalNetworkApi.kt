package io.embrace.android.embracesdk.instrumentation.huc

import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal class FakeInternalNetworkApi(
    var time: Long = 0,
    var captureNetworkBody: Boolean = true,
    var networkSpanForwardingEnabled: Boolean = false,
) : InternalNetworkApi {

    val networkRequests: MutableList<EmbraceNetworkRequest> = mutableListOf()
    var captureNetworkBodyError: Throwable? = null
    var captureNetworkBodyCount: Int = 0

    override fun getSdkCurrentTimeMs(): Long = time
    override fun isNetworkSpanForwardingEnabled(): Boolean = networkSpanForwardingEnabled
    override fun recordNetworkRequest(embraceNetworkRequest: EmbraceNetworkRequest) {
        networkRequests.add(embraceNetworkRequest)
    }

    override fun shouldCaptureNetworkBody(
        url: String,
        method: String,
    ): Boolean {
        captureNetworkBodyCount++
        captureNetworkBodyError?.let { throw it }
        return captureNetworkBody
    }

    override fun logSdkError(error: Throwable) {}
}
