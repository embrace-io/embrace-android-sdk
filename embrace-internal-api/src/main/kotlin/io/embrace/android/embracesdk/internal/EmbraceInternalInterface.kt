package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

/**
 * Provides an internal interface to Embrace that is intended for use by hosted SDKs as their sole source of communication
 * with the Android SDK. This is not publicly supported and methods can change at any time.
 */
interface EmbraceInternalInterface : InternalTracingApi {

    /**
     * Whether the Network Span Forwarding feature is enabled
     */
    fun isNetworkSpanForwardingEnabled(): Boolean

    /**
     * For the given URL and method, whether the response body should be captured for network request logging
     */
    fun shouldCaptureNetworkBody(url: String, method: String): Boolean

    /**
     * Logs an internal error to the Embrace SDK - this is not intended for public use.
     */
    fun logInternalError(message: String?, details: String?)

    /**
     * Logs an internal error to the Embrace SDK - this is not intended for public use.
     */
    fun logInternalError(error: Throwable)

    fun recordNetworkRequest(networkRequest: EmbraceNetworkRequest)
}
