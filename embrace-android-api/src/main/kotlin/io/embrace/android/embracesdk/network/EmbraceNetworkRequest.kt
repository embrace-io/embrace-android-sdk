package io.embrace.android.embracesdk.network

import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.http.HttpMethod
import java.util.Locale

/**
 * This class is used to create manually-recorded network requests.
 */
public class EmbraceNetworkRequest private constructor(

    /**
     * The request's URL. Must start with http:// or https://
     */
    public val url: String,

    /**
     * The request's method. Must be one of the following: GET, PUT, POST, DELETE, PATCH.
     */
    public val httpMethod: String,

    /**
     * The time the request started.
     */
    public val startTime: Long,

    /**
     * The time the request ended. Must be greater than the startTime.
     */
    public val endTime: Long,

    /**
     * The number of bytes sent.
     */
    public val bytesSent: Long?,

    /**
     * The number of bytes received.
     */
    public val bytesReceived: Long?,

    /**
     * The response status of the request. Must be in the range 100 to 599.
     */
    public val responseCode: Int?,

    /**
     * Error type that describes a non-HTTP error, e.g. a connection error.
     */
    public val errorType: String?,

    /**
     * Error message that describes a non-HTTP error, e.g. a connection error.
     */
    public val errorMessage: String?,

    /**
     * Optional trace ID that can be used to trace a particular request. Max length is 64 characters.
     */
    public val traceId: String?,

    /**
     * Optional W3C-compliant traceparent representing the network call that is being recorded
     */
    public val w3cTraceparent: String?,

    /**
     * Network capture data for the request.
     */
    public val networkCaptureData: NetworkCaptureData?,
) {

    /**
     * Error object that describes a non-HTTP error, e.g. a connection error.
     */
    public val error: Throwable? = null

    public val bytesIn: Long
        get() = bytesReceived ?: 0

    public val bytesOut: Long
        get() = bytesSent ?: 0

    public companion object {

        /**
         * Construct a new [EmbraceNetworkRequest] instance where a HTTP response was returned.
         * If no response was returned, use [.fromIncompleteRequest]
         * instead.
         *
         * @param url           the URL of the request.
         * @param httpMethod    the HTTP method of the request.
         * @param startTime     the start time of the request.
         * @param endTime       the end time of the request.
         * @param bytesSent     the number of bytes sent.
         * @param bytesReceived the number of bytes received.
         * @param statusCode    the status code of the response.
         * @param traceId       the trace ID of the request, used for distributed tracing.
         * @return a new [EmbraceNetworkRequest] instance.
         */
        @JvmStatic
        @JvmOverloads
        public fun fromCompletedRequest(
            url: String,
            httpMethod: HttpMethod,
            startTime: Long,
            endTime: Long,
            bytesSent: Long,
            bytesReceived: Long,
            statusCode: Int,
            traceId: String? = null,
            w3cTraceparent: String? = null,
            networkCaptureData: NetworkCaptureData? = null,
        ): EmbraceNetworkRequest {
            return EmbraceNetworkRequest(
                url,
                httpMethod.asString(),
                startTime,
                endTime,
                bytesSent,
                bytesReceived,
                statusCode,
                null,
                null,
                traceId,
                w3cTraceparent,
                networkCaptureData
            )
        }

        /**
         * Construct a new [EmbraceNetworkRequest] instance where a HTTP response was not returned.
         * If a response was returned, use [.fromCompletedRequest]
         * instead.
         *
         * @param url          the URL of the request.
         * @param httpMethod   the HTTP method of the request.
         * @param startTime    the start time of the request.
         * @param endTime      the end time of the request.
         * @param errorType    the error type that occurred.
         * @param errorMessage the error message
         * @return a new [EmbraceNetworkRequest] instance.
         */
        @JvmStatic
        @JvmOverloads
        public fun fromIncompleteRequest(
            url: String,
            httpMethod: HttpMethod,
            startTime: Long,
            endTime: Long,
            errorType: String,
            errorMessage: String,
            traceId: String? = null,
            w3cTraceparent: String? = null,
            networkCaptureData: NetworkCaptureData? = null,
        ): EmbraceNetworkRequest {
            return EmbraceNetworkRequest(
                url,
                httpMethod.asString(),
                startTime,
                endTime,
                null,
                null,
                null,
                errorType,
                errorMessage,
                traceId,
                w3cTraceparent,
                networkCaptureData
            )
        }

        internal fun HttpMethod.asString(): String = name.uppercase(Locale.getDefault())
    }
}
