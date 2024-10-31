@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.LogType
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.internal.payload.TapBreadcrumb
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

/**
 * Provides an internal interface to Embrace that is intended for use by hosted SDKs as their sole source of communication
 * with the Android SDK. This is not publicly supported and methods can change at any time.
 */
interface EmbraceInternalInterface : InternalTracingApi {
    /**
     * See [Embrace.logInfo]
     */
    fun logInfo(
        message: String,
        properties: Map<String, Any>?
    )

    /**
     * See [Embrace.logWarning]
     */
    fun logWarning(
        message: String,
        properties: Map<String, Any>?,
        stacktrace: String?
    )

    /**
     * See [Embrace.logError]
     */
    fun logError(
        message: String,
        properties: Map<String, Any>?,
        stacktrace: String?,
        isException: Boolean
    )

    /**
     * Backwards compatible way for hosted SDKs to log a handled exception with different log levels
     */
    fun logHandledException(
        throwable: Throwable,
        type: LogType,
        properties: Map<String, Any>?,
        customStackTrace: Array<StackTraceElement>?
    )

    /**
     * See [Embrace.recordNetworkRequest]
     */
    fun recordCompletedNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        bytesSent: Long,
        bytesReceived: Long,
        statusCode: Int,
        traceId: String?,
        networkCaptureData: NetworkCaptureData?
    )

    /**
     * See [Embrace.recordNetworkRequest]
     */
    fun recordIncompleteNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        error: Throwable?,
        traceId: String?,
        networkCaptureData: NetworkCaptureData?
    )

    /**
     * See [Embrace.recordNetworkRequest]
     */
    fun recordIncompleteNetworkRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        errorType: String?,
        errorMessage: String?,
        traceId: String?,
        networkCaptureData: NetworkCaptureData?
    )

    /**
     * Record a network request.
     *
     * @param embraceNetworkRequest the request to be recorded
     */
    fun recordNetworkRequest(
        embraceNetworkRequest: EmbraceNetworkRequest
    )

    /**
     * Logs a tap on a screen element.
     *
     * @param point       the coordinates of the screen tap
     * @param elementName the name of the element which was tapped
     * @param type        the type of tap that occurred
     */
    fun logTap(point: Pair<Float?, Float?>, elementName: String, type: TapBreadcrumb.TapBreadcrumbType)

    /**
     * Logs a tap on a Compose screen element.
     *
     * @param point       the coordinates of the screen tap
     * @param elementName the name of the element which was tapped
     */
    fun logComposeTap(point: Pair<Float, Float>, elementName: String)

    /**
     * For the given URL and method, whether the response body should be captured for network request logging
     */
    fun shouldCaptureNetworkBody(url: String, method: String): Boolean

    /**
     * Whether the Network Span Forwarding feature is enabled
     */
    fun isNetworkSpanForwardingEnabled(): Boolean

    /**
     * Return internal time the SDK is using in milliseconds. It is equivalent to [System.currentTimeMillis] assuming the system clock did
     * not change after the SDK has started.
     */
    fun getSdkCurrentTime(): Long

    /**
     * Whether the ANR capture service is enabled
     */
    fun isAnrCaptureEnabled(): Boolean

    /**
     * Whether the native crash capture is enabled
     */
    fun isNdkEnabled(): Boolean

    /**
     * Logs an internal error to the Embrace SDK - this is not intended for public use.
     */
    fun logInternalError(message: String?, details: String?)

    /**
     * Logs an internal error to the Embrace SDK - this is not intended for public use.
     */
    fun logInternalError(error: Throwable)

    /**
     * Stop the Embrace SDK and disable its functionality
     */
    fun stopSdk()
}
