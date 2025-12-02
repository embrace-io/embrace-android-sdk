package io.embrace.android.embracesdk.internal.instrumentation.okhttp

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.instrumentation.network.CUSTOM_TRACE_ID_HEADER_NAME
import io.embrace.android.embracesdk.internal.instrumentation.network.DefaultTraceparentGenerator
import io.embrace.android.embracesdk.internal.instrumentation.network.HttpNetworkRequest
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkRequestDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.TraceparentGenerator
import io.embrace.android.embracesdk.internal.instrumentation.network.getOverriddenURLString
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.okhttp3.EmbraceCustomPathException
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
 * Captures OkHttp requests as telemetry.
 */
internal class OkHttpDataSource(
    args: InstrumentationArgs,
    private val networkRequestDataSourceProvider: () -> NetworkRequestDataSource?,
    private val networkCaptureDataSourceProvider: () -> NetworkCaptureDataSource?,
    // A clock that mirrors the one used by OkHttp to get timestamps
    private val systemClock: Clock = Clock { System.currentTimeMillis() },
    private val traceparentGenerator: TraceparentGenerator = DefaultTraceparentGenerator,
) : DataSourceImpl(
    args = args,
    limitStrategy = NoopLimitStrategy, // always allow the OkHttp request chain to proceed
) {

    private val networkRequestDataSource: NetworkRequestDataSource?
        get() = networkRequestDataSourceProvider()

    private val networkCaptureDataSource: NetworkCaptureDataSource?
        get() = networkCaptureDataSourceProvider()

    internal fun interceptRequest(chain: Interceptor.Chain, type: InterceptorType): Response? =
        when (type) {
            InterceptorType.APPLICATION -> interceptApplicationRequest(chain)
            InterceptorType.NETWORK -> interceptNetworkRequest(chain)
        }

    private fun interceptApplicationRequest(chain: Interceptor.Chain): Response? {
        val startTime = clock.now()
        val request: Request = chain.request()
        return try {
            // we are not interested in response, just proceed
            chain.proceed(request)
        } catch (e: EmbraceCustomPathException) {
            val urlString =
                getOverriddenURLString(EmbraceOkHttpPathOverrideRequest(request), e.customPath)
            val cause = e.cause
            recordNetworkError(urlString, request, startTime, cause)
            throw e
        } catch (e: Exception) {
            // we are interested in errors.
            val urlString = getOverriddenURLString(EmbraceOkHttpPathOverrideRequest(request))
            val cause = e
            recordNetworkError(urlString, request, startTime, cause)
            throw e
        }
    }

    private fun recordNetworkError(
        urlString: String,
        request: Request,
        startTime: Long,
        cause: Throwable?,
    ) {
        networkRequestDataSource?.recordNetworkRequest(
            HttpNetworkRequest(
                url = urlString,
                httpMethod = request.method,
                startTime = startTime,
                endTime = clock.now(),
                errorType = cause?.javaClass?.canonicalName ?: UNKNOWN_EXCEPTION,
                errorMessage = cause?.message ?: UNKNOWN_MESSAGE,
                traceId = request.header(CUSTOM_TRACE_ID_HEADER_NAME),
                w3cTraceparent = if (configService.networkSpanForwardingBehavior.isNetworkSpanForwardingEnabled()) {
                    request.header(
                        TRACEPARENT_HEADER_NAME
                    )
                } else {
                    null
                }
            )
        )
    }

    private fun interceptNetworkRequest(chain: Interceptor.Chain): Response? {
        // If the SDK has not started, don't do anything
        val originalRequest: Request = chain.request()

        val networkSpanForwardingEnabled =
            configService.networkSpanForwardingBehavior.isNetworkSpanForwardingEnabled()
        var traceparent: String? = null
        if (networkSpanForwardingEnabled && originalRequest.header(TRACEPARENT_HEADER_NAME) == null) {
            traceparent = traceparentGenerator.generateW3cTraceparent()
        }
        val request =
            if (traceparent == null) {
                originalRequest
            } else {
                originalRequest.newBuilder()
                    .header(TRACEPARENT_HEADER_NAME, traceparent).build()
            }

        // Take a snapshot of the difference in the system and SDK clocks and send the request along the chain
        val offset = sdkClockOffset()
        val networkResponse: Response = chain.proceed(request)

        // Get response and determine the size of the body
        var contentLength: Long? = getContentLengthFromHeader(networkResponse)

        if (contentLength == null) {
            // If we get the body for a server-sent events stream, then we will wait forever
            contentLength =
                getContentLengthFromBody(
                    networkResponse,
                    networkResponse.header(CONTENT_TYPE_HEADER_NAME, null)
                )
        }

        if (contentLength == null) {
            // Set the content length to 0 if we can't determine it
            contentLength = 0L
        }

        var response: Response = networkResponse
        var networkCaptureData: HttpNetworkRequest.HttpRequestBody? = null
        val shouldCaptureNetworkData = networkCaptureDataSourceProvider()?.shouldCaptureNetworkBody(
            request.url.toString(),
            request.method
        ) ?: false

        // If we need to capture the network response body,
        if (shouldCaptureNetworkData) {
            if (ENCODING_GZIP.equals(
                    networkResponse.header(CONTENT_ENCODING_HEADER_NAME, null),
                    ignoreCase = true
                ) &&
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

        val req = HttpNetworkRequest(
            url = getOverriddenURLString(EmbraceOkHttpPathOverrideRequest(request)),
            httpMethod = request.method,
            startTime = response.sentRequestAtMillis + offset,
            endTime = response.receivedResponseAtMillis + offset,
            bytesSent = request.body?.contentLength() ?: 0,
            bytesReceived = contentLength,
            statusCode = response.code,
            traceId = request.header(CUSTOM_TRACE_ID_HEADER_NAME),
            w3cTraceparent = if (configService.networkSpanForwardingBehavior.isNetworkSpanForwardingEnabled()) {
                request.header(
                    TRACEPARENT_HEADER_NAME
                )
            } else {
                null
            },
            body = networkCaptureData
        )
        networkRequestDataSource?.recordNetworkRequest(req)
        networkCaptureDataSource?.recordNetworkRequest(req)
        return response
    }

    private fun getContentLengthFromHeader(networkResponse: Response): Long? {
        var contentLength: Long? = null
        val contentLengthHeaderValue = networkResponse.header(CONTENT_LENGTH_HEADER_NAME, null)
        if (contentLengthHeaderValue != null) {
            try {
                contentLength = contentLengthHeaderValue.toLong()
            } catch (ignored: Exception) {
                // Ignore
            }
        }
        return contentLength
    }

    private fun getContentLengthFromBody(networkResponse: Response, contentType: String?): Long? {
        var contentLength: Long? = null

        // Tolerant of a charset specified in header, e.g. Content-Type: text/event-stream;charset=UTF-8
        val serverSentEvent =
            contentType != null && contentType.startsWith(CONTENT_TYPE_EVENT_STREAM)
        if (!serverSentEvent) {
            try {
                val body = networkResponse.body
                if (body != null) {
                    val source = body.source()
                    source.request(Long.MAX_VALUE)
                    contentLength = source.buffer.size
                }
            } catch (ignored: Exception) {
                // Ignore
            }
        }

        return contentLength
    }

    private fun getNetworkCaptureData(
        request: Request,
        response: Response,
    ): HttpNetworkRequest.HttpRequestBody {
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
            dataCaptureErrorMessage =
                "There were errors in capturing the following part(s) of the network call: %s$errors"
            logger.trackInternalError(
                InternalErrorType.DATA_SOURCE_DATA_CAPTURE_FAIL,
                RuntimeException(
                    "Failure during the building of NetworkCaptureData. $dataCaptureErrorMessage",
                    e
                )
            )
        }
        return HttpNetworkRequest.HttpRequestBody(
            requestHeaders = requestHeaders,
            requestQueryParams = requestQueryParams,
            capturedRequestBody = requestBodyBytes,
            responseHeaders = responseHeaders,
            capturedResponseBody = responseBodyBytes,
            dataCaptureErrorMessage = dataCaptureErrorMessage
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
            logger.trackInternalError(
                InternalErrorType.DATA_SOURCE_DATA_CAPTURE_FAIL,
                e
            )
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

        val sdkTime1 = clock.now()
        val systemTime1 = systemClock.now()
        val sdkTime2 = clock.now()
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
        private const val TRACEPARENT_HEADER_NAME = "traceparent"
        private const val UNKNOWN_EXCEPTION = "Unknown"
        private const val UNKNOWN_MESSAGE =
            "An error occurred during the execution of this network request"
        private const val ENCODING_GZIP = "gzip"
        private const val CONTENT_LENGTH_HEADER_NAME = "Content-Length"
        private const val CONTENT_ENCODING_HEADER_NAME = "Content-Encoding"
        private const val CONTENT_TYPE_HEADER_NAME = "Content-Type"
        private const val CONTENT_TYPE_EVENT_STREAM = "text/event-stream"
        private val networkCallDataParts = arrayOf(
            "Response Headers",
            "Request Headers",
            "Query Parameters",
            "Request Body",
            "Response Body"
        )
    }
}
