package io.embrace.android.embracesdk.instrumentation.huc

import io.embrace.android.embracesdk.fakes.FakeEmbraceInternalInterface
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal class FakeInternalNetworkApi(
    var internalInterface: FakeEmbraceInternalInterface = FakeEmbraceInternalInterface(),
    var time: Long = 0,
    var started: Boolean = true,
) : InternalNetworkApi {
    override fun getSdkCurrentTimeMs(): Long = time
    override fun isStarted(): Boolean = started
    override fun isNetworkSpanForwardingEnabled(): Boolean = internalInterface.isNetworkSpanForwardingEnabled()
    override fun recordNetworkRequest(
        embraceNetworkRequest: EmbraceNetworkRequest,
    ) = internalInterface.recordNetworkRequest(
        embraceNetworkRequest
    )

    override fun shouldCaptureNetworkBody(
        url: String,
        method: String,
    ): Boolean = internalInterface.shouldCaptureNetworkBody(url, method)

    override fun logInternalError(error: Throwable) = internalInterface.logInternalError(error)
}
