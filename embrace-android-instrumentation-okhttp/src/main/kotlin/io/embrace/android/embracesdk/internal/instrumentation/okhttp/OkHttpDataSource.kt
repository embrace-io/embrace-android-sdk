package io.embrace.android.embracesdk.internal.instrumentation.okhttp

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.offset
import io.embrace.android.embracesdk.internal.instrumentation.network.CUSTOM_TRACE_ID_HEADER_NAME
import io.embrace.android.embracesdk.internal.instrumentation.network.HttpNetworkRequest
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkRequestDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.RequestEndData
import io.embrace.android.embracesdk.internal.instrumentation.network.RequestStartData
import io.embrace.android.embracesdk.internal.instrumentation.network.getOverriddenURLString
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.okhttp3.EmbraceCustomPathException
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.http.RealResponseBody
import okhttp3.internal.http.promisesBody
import okio.Buffer
import okio.GzipSource
import okio.buffer
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Captures OkHttp requests as telemetry.
 */
internal class OkHttpDataSource(
    args: InstrumentationArgs,
    private val networkRequestDataSourceProvider: () -> NetworkRequestDataSource?,
    private val networkCaptureDataSourceProvider: () -> NetworkCaptureDataSource?,
    // A clock that mirrors the one used by OkHttp to get timestamps
    private val systemClock: Clock = Clock { System.currentTimeMillis() },
) : DataSourceImpl(
    args = args,
    limitStrategy = NoopLimitStrategy, // always allow the OkHttp request chain to proceed
    instrumentationName = "okhttp_data_source"
) {
    /**
     * A map that stashes instrumentation information for a request so that different interceptors can reference the same instance.
     * The data for each call will be cleaned up when the result of the request is recorded.
     */
    private val activeCalls = ConcurrentHashMap<Call, CallData>()
    private val networkRequestDataSource: NetworkRequestDataSource?
        get() = networkRequestDataSourceProvider()

    private val networkCaptureDataSource: NetworkCaptureDataSource?
        get() = networkCaptureDataSourceProvider()

    internal fun interceptRequest(chain: Interceptor.Chain, type: InterceptorType): Response? {
        val call = chain.call()
        val callData = activeCalls[call] ?: startRequestInstrumentation(chain.request(), call)
        return when (type) {
            InterceptorType.APPLICATION -> interceptApplicationRequest(chain, callData)
            InterceptorType.NETWORK -> interceptNetworkRequest(chain, callData)
        }
    }

    private fun startRequestInstrumentation(request: Request, call: Call): CallData? {
        // Take a snapshot of the difference in the system and SDK clocks and use it normalize the timestamps used by OkHttp
        val systemClockStartTime = systemClock.now()
        val clockOffset = clock.offset(systemClock)
        val sdkClockStartTime = systemClockStartTime + clockOffset
        val callId = networkRequestDataSource?.startRequest(
            RequestStartData(
                url = getOverriddenURLString(EmbraceOkHttpPathOverrideRequest(request)),
                httpMethod = request.method,
                sdkClockStartTime = sdkClockStartTime,
            )
        ) ?: return null

        return CallData(
            id = callId,
            clockOffset = clockOffset,
            sdkClockStartTime = sdkClockStartTime
        ).also {
            activeCalls[call] = it
        }
    }

    private fun interceptApplicationRequest(chain: Interceptor.Chain, callData: CallData?): Response? {
        val request: Request = chain.request()
        return try {
            // we are not interested in response, just proceed
            chain.proceed(request)
        } catch (e: EmbraceCustomPathException) {
            val urlString = getOverriddenURLString(EmbraceOkHttpPathOverrideRequest(request), e.customPath)
            callData?.recordNetworkError(chain.call(), urlString, request, e.cause)
            throw e
        } catch (e: Exception) {
            // we are interested in errors.
            val urlString = getOverriddenURLString(EmbraceOkHttpPathOverrideRequest(request))
            callData?.recordNetworkError(chain.call(), urlString, request, e)
            throw e
        }
    }

    private fun CallData.recordNetworkError(
        call: Call,
        urlString: String,
        request: Request,
        cause: Throwable?,
    ) {
        try {
            networkRequestDataSource?.endRequest(
                RequestEndData(
                    id = id,
                    url = urlString,
                    sdkClockStartTime = sdkClockStartTime,
                    sdkClockEndTime = clock.now(),
                    statusCode = null,
                    errorType = cause?.javaClass?.canonicalName ?: UNKNOWN_EXCEPTION,
                    errorMessage = cause?.message ?: UNKNOWN_MESSAGE,
                    traceId = request.header(CUSTOM_TRACE_ID_HEADER_NAME),
                )
            )
        } finally {
            activeCalls.remove(call)
        }
    }

    private fun interceptNetworkRequest(chain: Interceptor.Chain, callData: CallData?): Response {
        val originalRequest: Request = chain.request()

        // Skip this interceptor if this call isn't being instrumented
        if (callData == null) {
            return chain.proceed(originalRequest)
        }

        val isNetworkSpanForwardingEnabled = configService.networkSpanForwardingBehavior.isNetworkSpanForwardingEnabled()
        // Inject the started span's W3C traceparent representation as a header if network span forwarding is enabled
        val request = if (isNetworkSpanForwardingEnabled) {
            originalRequest.newBuilder().header(TRACEPARENT_HEADER_NAME, callData.id).build()
        } else {
            originalRequest
        }

        // Let the request execution proceed and obs
        val networkResponse: Response = chain.proceed(request)

        // Get the size of the body from the response header if possible
        // Failing that, try to count the bytes in the body itself
        // If neither works, set the content length to 0
        val contentLength: Long = getContentLengthFromHeader(networkResponse)
            ?: getContentLengthFromBody(
                networkResponse = networkResponse,
                contentType = networkResponse.header(CONTENT_TYPE_HEADER_NAME, null)
            ) ?: 0L

        var response: Response = networkResponse
        val requestEndData = createRequestEndData(request, networkResponse, callData, contentLength)
        try {
            networkRequestDataSource?.endRequest(requestEndData)
        } finally {
            activeCalls.remove(chain.call())
        }

        val shouldCaptureNetworkData = networkCaptureDataSourceProvider()?.shouldCaptureNetworkBody(
            request.url.toString(),
            request.method
        ) ?: false
        if (shouldCaptureNetworkData && networkCaptureDataSource != null) {
            // Decompress any response body that is gzipped so it can be captured in plain text
            if (ENCODING_GZIP.equals(
                    networkResponse.header(CONTENT_ENCODING_HEADER_NAME, null),
                    ignoreCase = true
                ) && networkResponse.promisesBody()
            ) {
                networkResponse.body?.also {
                    response = it.decompressResponseBody(networkResponse, request)
                }
            }

            val networkCaptureData = getNetworkCaptureData(request, response)
            networkCaptureDataSource?.recordNetworkRequest(
                requestEndData.createHttpNetworkRequest(
                    httpMethod = request.method,
                    w3cTraceparent = if (isNetworkSpanForwardingEnabled) {
                        callData.id
                    } else {
                        null
                    },
                    networkCaptureData = networkCaptureData
                )
            )
        }

        return response
    }

    private fun RequestEndData.createHttpNetworkRequest(
        httpMethod: String,
        w3cTraceparent: String?,
        networkCaptureData: HttpNetworkRequest.HttpRequestBody?,
    ): HttpNetworkRequest = HttpNetworkRequest(
        url = url,
        httpMethod = httpMethod,
        startTime = sdkClockStartTime,
        endTime = sdkClockEndTime,
        bytesSent = bytesSent,
        bytesReceived = bytesReceived,
        statusCode = statusCode,
        traceId = traceId,
        w3cTraceparent = w3cTraceparent,
        body = networkCaptureData
    )

    private fun createRequestEndData(
        request: Request,
        response: Response,
        callData: CallData,
        contentLength: Long,
    ): RequestEndData = RequestEndData(
        id = callData.id,
        url = getOverriddenURLString(EmbraceOkHttpPathOverrideRequest(request)),
        sdkClockStartTime = response.sentRequestAtMillis + callData.clockOffset,
        sdkClockEndTime = response.receivedResponseAtMillis + callData.clockOffset,
        bytesSent = request.body?.contentLength() ?: 0,
        bytesReceived = contentLength,
        statusCode = response.code,
        traceId = request.header(CUSTOM_TRACE_ID_HEADER_NAME),
    )

    private fun ResponseBody.decompressResponseBody(
        networkResponse: Response,
        request: Request,
    ): Response {
        val strippedHeaders = networkResponse.headers.newBuilder()
            .removeAll(CONTENT_ENCODING_HEADER_NAME)
            .removeAll(CONTENT_LENGTH_HEADER_NAME)
            .build()
        val realResponseBody = RealResponseBody(
            networkResponse.header(CONTENT_TYPE_HEADER_NAME, null),
            -1L,
            GzipSource(source()).buffer()
        )
        val responseBuilder = networkResponse.newBuilder().request(request)
        responseBuilder.headers(strippedHeaders)
        responseBuilder.body(realResponseBody)
        return responseBuilder.build()
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
     * Data used for instrumenting each unique call so multiple sources can reference the same call.
     */
    private data class CallData(
        val id: String,
        val clockOffset: Long,
        val sdkClockStartTime: Long,
    )

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
