package io.embrace.android.embracesdk.okhttp3

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.InternalApi
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
public class EmbraceOkHttp3NetworkInterceptor internal constructor(
    private val embrace: Embrace,

    // A clock that mirrors the one used by OkHttp to get timestamps
    private val systemClock: Clock
) : Interceptor {
    public constructor() : this(Embrace.getInstance(), Clock { System.currentTimeMillis() })

    @Suppress("ComplexMethod")
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest: Request = chain.request()
        if (!embrace.isStarted || embrace.internalInterface.isInternalNetworkCaptureDisabled()) {
            return chain.proceed(originalRequest)
        }
        val offset = sdkClockOffset()
        val networkSpanForwardingEnabled = embrace.internalInterface.isNetworkSpanForwardingEnabled()
        var traceparent: String? = null
        if (networkSpanForwardingEnabled && originalRequest.header(TRACEPARENT_HEADER_NAME) == null) {
            traceparent = embrace.generateW3cTraceparent()
        }
        val request =
            if (traceparent == null) originalRequest else originalRequest.newBuilder().header(TRACEPARENT_HEADER_NAME, traceparent).build()
        val networkResponse: Response = chain.proceed(request)
        val responseBuilder: Response.Builder = networkResponse.newBuilder().request(request)
        var contentLength: Long? = null
        // Try to get the content length from the header
        val contentLengthHeaderValue = networkResponse.header(CONTENT_LENGTH_HEADER_NAME)
        if (contentLengthHeaderValue != null) {
            try {
                contentLength = contentLengthHeaderValue.toLong()
            } catch (ex: Exception) {
                // Ignore
            }
        }

        // If we get the body for a server-sent events stream, then we will wait forever
        val contentType = networkResponse.header(CONTENT_TYPE_HEADER_NAME)

        // Tolerant of a charset specified in header,
        // e.g. Content-Type: text/event-stream;charset=UTF-8
        val serverSentEvent = contentType != null && contentType.startsWith(CONTENT_TYPE_EVENT_STREAM)
        if (!serverSentEvent && contentLength == null) {
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
        if (contentLength == null) {
            // Otherwise default to zero
            contentLength = 0L
        }
        val shouldCaptureNetworkData = embrace.internalInterface.shouldCaptureNetworkBody(request.url.toString(), request.method)
        if (shouldCaptureNetworkData &&
            ENCODING_GZIP.equals(networkResponse.header(CONTENT_ENCODING_HEADER_NAME), ignoreCase = true) &&
            networkResponse.promisesBody()
        ) {
            val body = networkResponse.body
            if (body != null) {
                val strippedHeaders = networkResponse.headers.newBuilder()
                    .removeAll(CONTENT_ENCODING_HEADER_NAME)
                    .removeAll(CONTENT_LENGTH_HEADER_NAME)
                    .build()
                val realResponseBody = RealResponseBody(
                    contentType,
                    -1L,
                    GzipSource(body.source()).buffer()
                )
                responseBuilder.headers(strippedHeaders)
                responseBuilder.body(realResponseBody)
            }
        }
        val response: Response = responseBuilder.build()
        var networkCaptureData: NetworkCaptureData? = null
        if (shouldCaptureNetworkData) {
            networkCaptureData = getNetworkCaptureData(request, response)
        }
        embrace.recordNetworkRequest(
            EmbraceNetworkRequest.fromCompletedRequest(
                EmbraceHttpPathOverride.getURLString(EmbraceOkHttp3PathOverrideRequest(request)),
                HttpMethod.fromString(request.method),
                response.sentRequestAtMillis + offset,
                response.receivedResponseAtMillis + offset,
                if (request.body != null) request.body!!.contentLength() else 0,
                contentLength,
                response.code,
                request.header(embrace.traceIdHeader),
                if (networkSpanForwardingEnabled) request.header(TRACEPARENT_HEADER_NAME) else null,
                networkCaptureData
            )
        )
        return response
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
            embrace.logInternalError(
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
            embrace.logInternalError("Failed to capture okhttp request body.", e.javaClass.toString())
        }
        return null
    }

    /**
     * Get the difference between the SDK clock time and the time System.currentTimeMillis() returns, which is used by OkHttp for
     * determining client-side timestamps.
     */
    private fun sdkClockOffset(): Long {
        return embrace.internalInterface.getSdkCurrentTime() - systemClock.now()
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
