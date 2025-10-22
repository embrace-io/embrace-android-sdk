package io.embrace.android.embracesdk.instrumentation.huc

import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal class InternalNetworkApiImpl(
    private val sdkApi: SdkApi
) : InternalNetworkApi {
    private fun getInternalInterface(): EmbraceInternalInterface =
        checkNotNull(EmbraceInternalApi.internalInterface)

    override fun getSdkCurrentTimeMs(): Long = sdkApi.getSdkCurrentTimeMs()

    override fun isStarted(): Boolean = sdkApi.isStarted

    override fun generateW3cTraceparent(): String? = sdkApi.generateW3cTraceparent()

    override fun isNetworkSpanForwardingEnabled(): Boolean = getInternalInterface().isNetworkSpanForwardingEnabled()

    override fun recordNetworkRequest(embraceNetworkRequest: EmbraceNetworkRequest) =
        getInternalInterface().recordNetworkRequest(
            embraceNetworkRequest
        )

    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean =
        getInternalInterface().shouldCaptureNetworkBody(
            url,
            method
        )

    override fun logInternalError(error: Throwable) = getInternalInterface().logInternalError(error)
}
