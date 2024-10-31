package io.embrace.android.embracesdk.internal.network.http

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal class InternalNetworkApiImpl : InternalNetworkApi {

    private val embrace: Embrace
        get() = Embrace.getInstance()

    private fun getInternalInterface(): EmbraceInternalInterface =
        checkNotNull(EmbraceInternalApi.getInstance().internalInterface)

    override fun getSdkCurrentTime(): Long = getInternalInterface().getSdkCurrentTime()

    override fun isStarted(): Boolean = embrace.isStarted

    override fun getTraceIdHeader(): String = embrace.traceIdHeader

    override fun generateW3cTraceparent(): String? = embrace.generateW3cTraceparent()

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

internal var instance: InternalNetworkApi = InternalNetworkApiImpl()
