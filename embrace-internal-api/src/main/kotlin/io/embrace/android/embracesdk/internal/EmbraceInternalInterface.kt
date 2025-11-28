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

    /**
     * Retrieves the latest available remote config response from Embrace's server, or null if none is available.
     *
     * The return value is guaranteed to remain the same for the lifecycle of a process. Given this function is
     * expensive, it should only be called once and the value should be safely cached by the hybrid SDK.
     */
    fun getRemoteConfig(): Map<String, Any>?

    /**
     * This function can be called by the hybrid SDks to check whether % based features are enabled via
     * remote config. It does this by using the device ID and % threshold in the remote config response.
     * The return value can be true/false/null. If null is returned, then the default behaviour should be used.
     * true/false will only be returned if they were explicitly set in the config response.
     *
     * The return value for a given parameter is guaranteed to remain the same for the lifecycle of a process.
     * It's preferred but not necessary for the hybrid SDK to cache the returned value.
     *
     * As an example, anr.pct_enabled could be obtained from the [getRemoteConfig] map and passed to this
     * function to determine whether ANR capture should be enabled or not.
     */
    fun isConfigFeatureEnabled(pctEnabled: Float?): Boolean?
}
