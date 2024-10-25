package io.embrace.android.embracesdk.okhttp3

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.network.http.EmbraceHttpPathOverride
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http.RealResponseBody
import okhttp3.internal.http.promisesBody
import okio.Buffer
import okio.GzipSource
import okio.buffer
import java.io.IOException
import kotlin.math.abs

/**
 * Custom OkHttp Interceptor implementation that will log the results of the network call
 * to Embrace.io.
 *
 * This interceptor will only intercept network request and responses from client app.
 * OkHttp network interceptors are added almost at the end of stack, they are closer to "Wire"
 * so they are able to see catch "real requests".
 *
 * Network Interceptors
 * - Able to operate on intermediate responses like redirects and retries.
 * - Not invoked for cached responses that short-circuit the network.
 * - Observe the data just as it will be transmitted over the network.
 * - Access to the Connection that carries the request.
 */
@InternalApi
class EmbraceOkHttp3NetworkInterceptor @JvmOverloads constructor(
    private val embrace: Embrace,
    private val embraceInternalApi: EmbraceInternalApi,
    // A clock that mirrors the one used by OkHttp to get timestamps
    private val systemClock: Clock = Clock { System.currentTimeMillis() }
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        // If the SDK has not started, don't do anything
        val originalRequest: Request = chain.request()
        if (!embrace.isStarted) {
            return chain.proceed(originalRequest)
        }

        val networkSpanForwardingEnabled = embraceInternalApi.internalInterface.isNetworkSpanForwardingEnabled()
        var traceparent: String? = null
        if (networkSpanForwardingEnabled && originalRequest.header(TRACEPARENT_HEADER_NAME) == null) {
            traceparent = embrace.generateW3cTraceparent()
        }
        val request =
            if (traceparent == null) originalRequest else originalRequest.newBuilder().header(TRACEPARENT_HEADER_NAME, traceparent).build()

        // Take a snapshot of the difference in the system and SDK clocks and send the request along the chain
        val offset = sdkClockOffset()
        val networkResponse: Response = chain.proceed(request)

        // Get response and determine the size of the body
        var contentLength: Long? = getContentLengthFromHeader(networkResponse)

        if (contentLength == null) {
            // If we get the body for a server-sent events stream, then we will wait forever
            contentLength = getContentLengthFromBody(networkResponse, networkResponse.header(CONTENT_TYPE_HEADER_NAME, null))
        }

        if (contentLength == null) {
            // Set the content length to 0 if we can't determine it
            contentLength = 0L
        }

        var response: Response = networkResponse
        var networkCaptureData: NetworkCaptureData? = null
        val shouldCaptureNetworkData = embraceInternalApi.internalInterface.shouldCaptureNetworkBody(request.url.toString(), request.method)

        // If we need to capture the network response body,
        if (shouldCaptureNetworkData) {
            if (ENCODING_GZIP.equals(networkResponse.header(CONTENT_ENCODING_HEADER_NAME, null), ignoreCase = true) &&
                networkResponse.promisesBody()
            ) {
                val body = networkResponse.body
                if (body != null) {
                    val strippedHeaders = networkResponse.headers.newBuilder()
                        .removeAll(CONTENT_ENCODING_HEADER_NAME)
                        .removeAll(CONTENT_LENGTH_HEADER_NAME)
                        .build()
                    val realResponseBody = RealResponseBody(
                        networkResponse.header(CONTENT_TYPE_HEADER_NAME, null),
                        -1L,
                        GzipSource(body.source()).buffer()
                    )
                    val responseBuilder = networkResponse.newBuilder().request(request)
                    responseBuilder.headers(strippedHeaders)
                    responseBuilder.body(realResponseBody)
                    response = responseBuilder.build()
                }
            }

            networkCaptureData = getNetworkCaptureData(request, response)
        }

        embrace.recordNetworkRequest(
            EmbraceNetworkRequest.fromCompletedRequest(
                EmbraceHttpPathOverride.getURLString(EmbraceOkHttp3PathOverrideRequest(request)),
                HttpMethod.fromString(request.method),
                response.sentRequestAtMillis + offset,
                response.receivedResponseAtMillis + offset,
                request.body?.contentLength() ?: 0,
                contentLength,
                response.code,
                request.header(embrace.traceIdHeader),
                if (networkSpanForwardingEnabled) request.header(TRACEPARENT_HEADER_NAME) else null,
                networkCaptureData
            )
        )
        return response
    }

    private fun getContentLengthFromHeader(networkResponse: Response): Long? {
        var contentLength: Long? = null
        val contentLengthHeaderValue = networkResponse.header(CONTENT_LENGTH_HEADER_NAME, null)
        if (contentLengthHeaderValue != null) {
            try {
                contentLength = contentLengthHeaderValue.toLong()
            } catch (ex: Exception) {
                // Ignore
            }
        }
        return contentLength
    }

    private fun getContentLengthFromBody(networkResponse: Response, contentType: String?): Long? {
        var contentLength: Long? = null

        // Tolerant of a charset specified in header, e.g. Content-Type: text/event-stream;charset=UTF-8
        val serverSentEvent = contentType != null && contentType.startsWith(CONTENT_TYPE_EVENT_STREAM)
        if (!serverSentEvent) {
            try {
                val body = networkResponse.body
                if (body != null) {
                    val source = body.source()
                    source.request(Long.MAX_VALUE)
                    contentLength = source.buffer.size
                }
            } catch (ex: Exception) {
                // Ignore
            }
        }

        return contentLength
    }

    private fun getNetworkCaptureData(request: Request, response: Response): NetworkCaptureData {
        var requestHeaders: Map<String, String>? = null
        var requestQueryParams: String? = null
        var responseHeaders: Map<String, String>? = null
        var requestBodyBytes: ByteArray? = null
        var responseBodyBytes: ByteArray? = null
        var dataCaptureErrorMessage: String? = null
        var partsAcquired = 0
        try {
            responseHeaders = getProcessedHeaders(response.headers.toMultimap())
            partsAcquired++
            requestHeaders = getProcessedHeaders(request.headers.toMultimap())
            partsAcquired++
            requestQueryParams = request.url.query
            partsAcquired++
            requestBodyBytes = getRequestBody(request)
            partsAcquired++
            if (response.promisesBody()) {
                val responseBody = response.body
                if (responseBody != null) {
                    val okResponseBodySource = responseBody.source()
                    okResponseBodySource.request(Int.MAX_VALUE.toLong())
                    responseBodyBytes = okResponseBodySource.buffer.snapshot().toByteArray()
                }
            }
        } catch (e: Exception) {
            val errors = StringBuilder()
            var i = partsAcquired
            while (i < 5) {
                errors.append("'").append(networkCallDataParts[i]).append("'")
                if (i != 4) {
                    errors.append(", ")
                }
                i++
            }
            dataCaptureErrorMessage = "There were errors in capturing the following part(s) of the network call: %s$errors"
            embraceInternalApi.internalInterface.logInternalError(
                RuntimeException("Failure during the building of NetworkCaptureData. $dataCaptureErrorMessage", e)
            )
        }
        return NetworkCaptureData(
            requestHeaders,
            requestQueryParams,
            requestBodyBytes,
            responseHeaders,
            responseBodyBytes,
            dataCaptureErrorMessage
        )
    }

    private fun getProcessedHeaders(properties: Map<String, List<String>>): Map<String, String> {
        val headers = HashMap<String, String>()
        for ((key, value1) in properties) {
            val builder = StringBuilder()
            for (value in value1) {
                builder.append(value)
            }
            headers[key] = builder.toString()
        }
        return headers
    }

    private fun getRequestBody(request: Request): ByteArray? {
        try {
            val requestCopy = request.newBuilder().build()
            val requestBody = requestCopy.body
            if (requestBody != null) {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                return buffer.readByteArray()
            }
        } catch (e: IOException) {
            embraceInternalApi.internalInterface.logInternalError("Failed to capture okhttp request body.", e.javaClass.toString())
        }
        return null
    }

    /**
     * Estimate the difference between the current time returned by the SDK clock and the system clock, the latter of which is used by
     * OkHttp to determine timestamps
     */
    private fun sdkClockOffset(): Long {
        // To ensure that the offset is the result of clock drift, we take two samples and ensure that their difference is less than 1ms
        // before we use the value. A 1 ms difference between the samples is possible given it could be the result of the time
        // "ticking over" to the next millisecond, but given the calls take the order of microseconds, it should not go beyond that.
        //
        // Any difference that is greater than 1 ms is likely the result of a change to the system clock during this process, or some
        // scheduling quirk that makes the result not trustworthy. In that case, we simply don't return an offset.

        val sdkTime1 = embraceInternalApi.internalInterface.getSdkCurrentTime()
        val systemTime1 = systemClock.now()
        val sdkTime2 = embraceInternalApi.internalInterface.getSdkCurrentTime()
        val systemTime2 = systemClock.now()

        val diff1 = sdkTime1 - systemTime1
        val diff2 = sdkTime2 - systemTime2

        return if (abs(diff1 - diff2) <= 1L) {
            (diff1 + diff2) / 2
        } else {
            0L
        }
    }

    internal companion object {
        internal const val ENCODING_GZIP = "gzip"
        internal const val CONTENT_LENGTH_HEADER_NAME = "Content-Length"
        internal const val CONTENT_ENCODING_HEADER_NAME = "Content-Encoding"
        internal const val CONTENT_TYPE_HEADER_NAME = "Content-Type"
        internal const val CONTENT_TYPE_EVENT_STREAM = "text/event-stream"
        internal const val TRACEPARENT_HEADER_NAME = "traceparent"
        private val networkCallDataParts = arrayOf(
            "Response Headers",
            "Request Headers",
            "Query Parameters",
            "Request Body",
            "Response Body"
        )
    }
}
