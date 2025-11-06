package io.embrace.android.embracesdk.instrumentation.huc

import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal class InternalNetworkApiImpl(
    private val sdkStateApi: SdkStateApi,
    private val instrumentationApi: InstrumentationApi,
    private val networkRequestApi: NetworkRequestApi,
    private val internalInterface: EmbraceInternalInterface,
) : InternalNetworkApi {
    override fun getSdkCurrentTimeMs(): Long = instrumentationApi.getSdkCurrentTimeMs()

    override fun isStarted(): Boolean = sdkStateApi.isStarted

    override fun isNetworkSpanForwardingEnabled(): Boolean = internalInterface.isNetworkSpanForwardingEnabled()

    override fun recordNetworkRequest(embraceNetworkRequest: EmbraceNetworkRequest) =
        networkRequestApi.recordNetworkRequest(
            embraceNetworkRequest
        )

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean =
        internalInterface.shouldCaptureNetworkBody(
            url = url,
            method = method
        )

    override fun logInternalError(error: Throwable) = internalInterface.logInternalError(error)
}
