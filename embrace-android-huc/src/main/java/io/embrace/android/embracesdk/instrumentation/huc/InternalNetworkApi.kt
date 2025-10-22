package io.embrace.android.embracesdk.instrumentation.huc

import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

internal interface InternalNetworkApi {
    fun isNetworkSpanForwardingEnabled(): Boolean
    fun recordNetworkRequest(embraceNetworkRequest: EmbraceNetworkRequest)
    fun shouldCaptureNetworkBody(url: String, method: String): Boolean
    fun logInternalError(error: Throwable)
    fun getSdkCurrentTimeMs(): Long
    fun isStarted(): Boolean
    fun generateW3cTraceparent(): String?
}

internal object NoopInternalNetworkApi : InternalNetworkApi {
    override fun isNetworkSpanForwardingEnabled(): Boolean = false
    override fun recordNetworkRequest(embraceNetworkRequest: EmbraceNetworkRequest) {}
    override fun shouldCaptureNetworkBody(url: String, method: String): Boolean = false
    override fun logInternalError(error: Throwable) {}
    override fun getSdkCurrentTimeMs(): Long = 0
    override fun isStarted(): Boolean = false
    override fun generateW3cTraceparent(): String? = null
}
