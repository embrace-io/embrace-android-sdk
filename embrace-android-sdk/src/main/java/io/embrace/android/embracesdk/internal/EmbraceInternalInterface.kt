@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.LogType
import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest

/**
 * Provides an internal interface to Embrace that is intended for use by hosted SDKs as their sole source of communication
 * with the Android SDK. This is not publicly supported and methods can change at any time.
 */
@InternalApi
public interface EmbraceInternalInterface : InternalTracingApi {
    /**
     * See [Embrace.logInfo]
     */
    public fun logInfo(
        message: String,
        properties: Map<String, Any>?
    )

    /**
     * See [Embrace.logWarning]
     */
    public fun logWarning(
        message: String,
        properties: Map<String, Any>?,
        stacktrace: String?
    )

    /**
     * See [Embrace.logError]
     */
    public fun logError(
        message: String,
        properties: Map<String, Any>?,
        stacktrace: String?,
        isException: Boolean
    )

    /**
     * Backwards compatible way for hosted SDKs to log a handled exception with different log levels
     */
    public fun logHandledException(
        throwable: Throwable,
        type: LogType,
        properties: Map<String, Any>?,
        customStackTrace: Array<StackTraceElement>?
    )

    /**
     * See [Embrace.recordNetworkRequest]
     */
    public fun recordCompletedNetworkRequest(
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
    public fun recordIncompleteNetworkRequest(
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
    public fun recordIncompleteNetworkRequest(
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
     * Record a network request and overwrite any previously recorded request with the same callId
     *
     * @param callId                the ID with which the request will be identified internally. The session will only contain one recorded
     *                              request with a given ID - last writer wins.
     * @param embraceNetworkRequest the request to be recorded
     */
    public fun recordAndDeduplicateNetworkRequest(
        callId: String,
        embraceNetworkRequest: EmbraceNetworkRequest,
        isStart: Boolean = false
    )

    /**
     * Logs a tap on a Compose screen element.
     *
     * @param point       the coordinates of the screen tap
     * @param elementName the name of the element which was tapped
     */
    public fun logComposeTap(point: Pair<Float, Float>, elementName: String)

    /**
     * For the given URL and method, whether the response body should be captured for network request logging
     */
    public fun shouldCaptureNetworkBody(url: String, method: String): Boolean

    /**
     * Mark that this application process was created in response to a notification
     */
    public fun setProcessStartedByNotification()

    /**
     * Whether the Network Span Forwarding feature is enabled
     */
    public fun isNetworkSpanForwardingEnabled(): Boolean

    /**
     * Return internal time the SDK is using in milliseconds. It is equivalent to [System.currentTimeMillis] assuming the system clock did
     * not change after the SDK has started.
     */
    public fun getSdkCurrentTime(): Long

    /**
     * Whether network capture has been disabled through an internal, not-publicly supported means
     */
    public fun isInternalNetworkCaptureDisabled(): Boolean

    /**
     * Whether the ANR capture service is enabled
     */
    public fun isAnrCaptureEnabled(): Boolean

    /**
     * Whether the native crash capture is enabled
     */
    public fun isNdkEnabled(): Boolean

    /**
     * Logs an internal error to the Embrace SDK - this is not intended for public use.
     */
    public fun logInternalError(message: String?, details: String?)

    /**
     * Logs an internal error to the Embrace SDK - this is not intended for public use.
     */
    public fun logInternalError(error: Throwable)

    /**
     * Stop the Embrace SDK and disable its functionality
     */
    public fun stopSdk()
}
