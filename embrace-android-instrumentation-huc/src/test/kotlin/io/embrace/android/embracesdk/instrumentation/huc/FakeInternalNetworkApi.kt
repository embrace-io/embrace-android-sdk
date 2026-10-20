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
    val loggedErrors: MutableList<Throwable> = mutableListOf()

    /**
     * When set, thrown whenever the SDK's clock is read, which the delegate does from inside the app's own calls.
     */
    var failure: Throwable? = null

    override fun getSdkCurrentTimeMs(): Long {
        failure?.let { throw it }
        return time
    }

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

    override fun logSdkError(error: Throwable) {
        loggedErrors.add(error)
    }
}
