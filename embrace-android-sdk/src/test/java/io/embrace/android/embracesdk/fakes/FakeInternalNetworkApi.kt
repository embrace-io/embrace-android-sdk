package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehaviorImpl.Companion.CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE
import io.embrace.android.embracesdk.internal.network.http.InternalNetworkApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal class FakeInternalNetworkApi(
    var internalInterface: FakeEmbraceInternalInterface = FakeEmbraceInternalInterface(),
    var time: Long = 0,
    var started: Boolean = true,
    var traceHeader: String = CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE,
    var w3cTraceparent: String? = "00-3c72a77a7b51af6fb3778c06d4c165ce-4c1d710fffc88e35-01"
) : InternalNetworkApi {
    override fun getSdkCurrentTime(): Long = time
    override fun isStarted(): Boolean = started
    override fun getTraceIdHeader(): String = traceHeader
    override fun generateW3cTraceparent(): String? = w3cTraceparent
    override fun isNetworkSpanForwardingEnabled(): Boolean = internalInterface.isNetworkSpanForwardingEnabled()
    override fun recordNetworkRequest(
        embraceNetworkRequest: EmbraceNetworkRequest
    ) = internalInterface.recordNetworkRequest(
        embraceNetworkRequest
    )
    override fun shouldCaptureNetworkBody(
        url: String,
        method: String
    ): Boolean = internalInterface.shouldCaptureNetworkBody(url, method)
    override fun logInternalError(error: Throwable) = internalInterface.logInternalError(error)
}
