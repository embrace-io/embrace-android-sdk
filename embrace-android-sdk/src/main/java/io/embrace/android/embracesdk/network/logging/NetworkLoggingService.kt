package io.embrace.android.embracesdk.network.logging

import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
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
     * @param url                the URL being called
     * @param httpMethod         the HTTP method
     * @param statusCode         the status code from the response
     * @param startTime          the start time of the request
     * @param endTime            the end time of the request
     * @param bytesSent          the number of bytes sent
     * @param bytesReceived      the number of bytes received
     * @param traceId            optional trace ID that can be used to trace a particular request
     * @param w3cTraceparent     optional W3C-compliant traceparent representing the network call that is being recorded
     * @param networkCaptureData the additional data captured if network body capture is enabled for the URL
     */
    @Suppress("LongParameterList")
    fun logNetworkCall(
        callId: String,
        url: String,
        httpMethod: String,
        statusCode: Int,
        startTime: Long,
        endTime: Long,
        bytesSent: Long,
        bytesReceived: Long,
        traceId: String?,
        w3cTraceparent: String?,
        networkCaptureData: NetworkCaptureData?
    )

    /**
     * Logs an exception which occurred when attempting to make a network call.
     *
     * @param callId             the unique ID of the call used for deduplication purposes
     * @param url                the URL being called
     * @param httpMethod         the HTTP method
     * @param startTime          the start time of the request
     * @param endTime            the end time of the request
     * @param errorType          the type of error being thrown
     * @param errorMessage       the error message
     * @param traceId            optional trace ID that can be used to trace a particular request
     * @param w3cTraceparent     optional W3C-compliant traceparent representing the network call that is being recorded
     * @param networkCaptureData the additional data captured if network body capture is enabled for the URL
     */
    fun logNetworkError(
        callId: String,
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        errorType: String?,
        errorMessage: String?,
        traceId: String?,
        w3cTraceparent: String?,
        networkCaptureData: NetworkCaptureData?
    )

    /**
     * Logs a network request. It might be a request that was completed successfully or one that failed.
     *
     * @param networkRequest the network request to log
     */
    fun logNetworkRequest(networkRequest: EmbraceNetworkRequest)

    /**
     * Logs a network request. It might be a request that was completed successfully or one that failed.
     * This is only used by the EmbraceUrlConnectionDelegate, it will deduplicate requests based on the callId.
     *
     * @param callId                the ID with which the request will be identified internally. The session will only contain one recorded
     *                              request with a given ID - last writer wins.
     * @param request the request to be recorded
     */
    fun logURLConnectionNetworkRequest(callId: String, request: EmbraceNetworkRequest)
}
