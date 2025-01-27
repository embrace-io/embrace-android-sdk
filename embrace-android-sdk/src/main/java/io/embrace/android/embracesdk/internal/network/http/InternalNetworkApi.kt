package io.embrace.android.embracesdk.internal.network.http

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
