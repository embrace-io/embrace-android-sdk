package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.payload.NetworkSessionV2

/**
 * Logs network calls made by the application. The Embrace SDK intercepts the calls and reports
 * them to the API.
 */
internal interface NetworkLoggingService {

    /**
     * Get the calls and counts of network calls (which exceed the limit) that haven't been associated with a session or background activity
     *
     * @return the network calls for the given session
     */
    fun getNetworkCallsSnapshot(): NetworkSessionV2

    /**
     * Logs a HTTP network call.
     *
     * @param callId             the unique ID of the call used for deduplication purposes
     * @param statusCode         the status code from the response
     * @param endTime            the end time of the request
     * @param bytesSent          the number of bytes sent
     * @param bytesReceived      the number of bytes received
     * @param networkCaptureData the additional data captured if network body capture is enabled for the URL
     */
    fun endNetworkRequest(
        callId: String,
        statusCode: Int,
        endTime: Long,
        bytesSent: Long,
        bytesReceived: Long,
        networkCaptureData: NetworkCaptureData?
    )

    /**
     * Logs an exception which occurred when attempting to make a network call.
     *
     * @param callId             the unique ID of the call used for deduplication purposes
     * @param endTime            the end time of the request
     * @param errorType          the type of error being thrown
     * @param errorMessage       the error message
     * @param networkCaptureData the additional data captured if network body capture is enabled for the URL
     */
    fun endNetworkRequestWithError(
        callId: String,
        endTime: Long,
        errorType: String?,
        errorMessage: String?,
        networkCaptureData: NetworkCaptureData?
    )

    fun startNetworkCall(
        callId: String,
        url: String,
        httpMethod: String,
        statusCode: Int,
        startTime: Long,
        traceId: String?,
        w3cTraceparent: String?,
    )
}
