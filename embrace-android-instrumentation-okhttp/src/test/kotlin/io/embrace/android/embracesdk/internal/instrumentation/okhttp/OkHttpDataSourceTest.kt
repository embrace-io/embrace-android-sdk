package io.embrace.android.embracesdk.internal.instrumentation.okhttp

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.fakes.FakeLogData
import io.embrace.android.embracesdk.fakes.FakeSpanToken
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkBehavior
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkCaptureDataSourceImpl
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkRequestDataSourceImpl
import io.embrace.android.embracesdk.internal.utils.NetworkUtils.getValidTraceId
import io.embrace.android.embracesdk.okhttp3.EmbraceCustomPathException
import io.embrace.opentelemetry.kotlin.semconv.ErrorAttributes
import io.embrace.opentelemetry.kotlin.semconv.ExceptionAttributes
import io.embrace.opentelemetry.kotlin.semconv.HttpAttributes
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.UrlAttributes
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.SocketException
import java.util.zip.GZIPOutputStream

@OptIn(IncubatingApi::class)
@RunWith(AndroidJUnit4::class)
internal class OkHttpDataSourceTest {

    companion object {
        private const val REQUEST_HEADER_NAME = "requestHeader"
        private const val REQUEST_HEADER_VALUE = "requestHeaderVal"
        private const val DEFAULT_QUERY_STRING = "param=yesPlease"
        private const val DEFAULT_PATH = "/test/default-path"
        private const val CUSTOM_PATH = "/test/custom-path"
        private const val REQUEST_BODY_STRING = "hey body"
        private const val REQUEST_BODY_SIZE = 8
        private const val RESPONSE_HEADER_NAME = "responseHeader"
        private const val RESPONSE_BODY =
            "{\"bodyString\" = \"stringstringstringstringstringstringstringstringstringstringstringstring\"}"
        private const val RESPONSE_BODY_SIZE = 91
        private const val RESPONSE_BODY_GZIPPED_SIZE = 43
        private const val RESPONSE_HEADER_VALUE = "responseHeaderVal"
        private const val TRACEPARENT_HEADER = "traceparent"
        private const val ENCODING_GZIP = "gzip"
        private const val CONTENT_LENGTH_HEADER_NAME = "Content-Length"
        private const val CONTENT_ENCODING_HEADER_NAME = "Content-Encoding"
        private const val CONTENT_TYPE_HEADER_NAME = "Content-Type"
        private const val CONTENT_TYPE_EVENT_STREAM = "text/event-stream"
        private const val UNKNOWN_EXCEPTION = "Unknown"
        private const val UNKNOWN_MESSAGE =
            "An error occurred during the execution of this network request"
    }

    private lateinit var server: MockWebServer
    private lateinit var sdkClock: FakeClock
    private lateinit var args: FakeInstrumentationArgs
    private lateinit var configService: FakeConfigService
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var getRequestBuilder: Request.Builder
    private lateinit var postRequestBuilder: Request.Builder
    private var preNetworkInterceptorBeforeRequestSupplier: (Request) -> Request =
        { request -> request }
    private var preNetworkInterceptorAfterResponseSupplier: (Response) -> Response =
        { response -> response }
    private var postNetworkInterceptorBeforeRequestSupplier: (Request) -> Request =
        { request -> request }
    private var postNetworkInterceptorAfterResponseSupplier: (Response) -> Response =
        { response -> response }
    private var isNetworkSpanForwardingEnabled = false
        set(value) {
            field = value
            configService.networkSpanForwardingBehavior = FakeNetworkSpanForwardingBehavior(value)
        }

    @Before
    fun setup() {
        server = MockWebServer()
        configService = FakeConfigService()
        sdkClock = FakeClock(System.currentTimeMillis())
        args = FakeInstrumentationArgs(
            application = ApplicationProvider.getApplicationContext(),
            configService = configService,
            clock = sdkClock
        )
        val networkRequestDataSource = NetworkRequestDataSourceImpl(args)
        val networkCaptureDataSource = NetworkCaptureDataSourceImpl(args)

        configService.apply {
            networkBehavior = FakeNetworkBehavior(
                rules = setOf(
                    NetworkCaptureRuleRemoteConfig(
                        id = "1",
                        method = "POST",
                        duration = 0,
                        urlRegex = "^.*$",
                        expiresIn = 60000,
                        statusCodes = setOf(200, 500)
                    ),
                )
            )
            networkSpanForwardingBehavior = FakeNetworkSpanForwardingBehavior()
        }

        val dataSource = OkHttpDataSource(
            args,
            { networkRequestDataSource },
            { networkCaptureDataSource },
        )
        val applicationInterceptor = EmbraceOkHttpInterceptor(InterceptorType.APPLICATION) { dataSource }
        val preNetworkInterceptorTestInterceptor = TestInspectionInterceptor(
            beforeRequestSent = { request ->
                preNetworkInterceptorBeforeRequestSupplier.invoke(
                    request
                )
            },
            afterResponseReceived = { response ->
                preNetworkInterceptorAfterResponseSupplier.invoke(
                    response
                )
            }
        )
        val networkInterceptor = EmbraceOkHttpInterceptor(InterceptorType.NETWORK) { dataSource }
        val postNetworkInterceptorTestInterceptor = TestInspectionInterceptor(
            beforeRequestSent = { request ->
                postNetworkInterceptorBeforeRequestSupplier.invoke(
                    request
                )
            },
            afterResponseReceived = { response ->
                postNetworkInterceptorAfterResponseSupplier.invoke(
                    response
                )
            }
        )
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(applicationInterceptor)
            .addNetworkInterceptor(preNetworkInterceptorTestInterceptor)
            .addNetworkInterceptor(networkInterceptor)
            .addNetworkInterceptor(postNetworkInterceptorTestInterceptor)
            .build()
        getRequestBuilder = Request.Builder()
            .url(server.url("$DEFAULT_PATH?$DEFAULT_QUERY_STRING"))
            .get()
            .header(REQUEST_HEADER_NAME, REQUEST_HEADER_VALUE)
        postRequestBuilder = Request.Builder()
            .url(server.url("$DEFAULT_PATH?$DEFAULT_QUERY_STRING"))
            .post(REQUEST_BODY_STRING.toRequestBody())
            .header(REQUEST_HEADER_NAME, REQUEST_HEADER_VALUE)
        isNetworkSpanForwardingEnabled = false
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `completed successful requests with uncompressed responses are recorded properly`() {
        preNetworkInterceptorAfterResponseSupplier = ::consumeBody
        server.enqueue(createBaseMockResponse().setBody(RESPONSE_BODY))
        runAndValidatePostRequest(RESPONSE_BODY_SIZE)

        server.enqueue(createBaseMockResponse().setBody(RESPONSE_BODY))
        runAndValidateGetRequest(RESPONSE_BODY_SIZE)
    }

    @Test
    fun `completed successful requests with gzipped responses are recorded properly`() {
        preNetworkInterceptorAfterResponseSupplier = ::consumeBody
        server.enqueue(createBaseMockResponse().setGzipBody(RESPONSE_BODY))
        runAndValidatePostRequest(RESPONSE_BODY_GZIPPED_SIZE)

        server.enqueue(createBaseMockResponse().setGzipBody(RESPONSE_BODY))
        runAndValidateGetRequest(RESPONSE_BODY_GZIPPED_SIZE)
    }

    @Test
    fun `completed unsuccessful requests are recorded properly`() {
        server.enqueue(createBaseMockResponse(500).setGzipBody(RESPONSE_BODY))
        runAndValidatePostRequest(
            expectedResponseBodySize = RESPONSE_BODY_GZIPPED_SIZE,
            expectedHttpStatus = 500
        )
    }

    @Test
    fun `completed requests with custom paths are recorded properly`() {
        server.enqueue(createBaseMockResponse().setGzipBody(RESPONSE_BODY))
        postRequestBuilder.header("x-emb-path", CUSTOM_PATH)
        runAndValidatePostRequest(
            expectedResponseBodySize = RESPONSE_BODY_GZIPPED_SIZE,
            expectedPath = CUSTOM_PATH
        )
    }

    @Test
    fun `incomplete requests with custom paths are recorded properly`() {
        server.enqueue(createBaseMockResponse().setGzipBody(RESPONSE_BODY))
        postRequestBuilder.header("x-emb-path", CUSTOM_PATH)
        runAndValidatePostRequest(
            expectedResponseBodySize = RESPONSE_BODY_GZIPPED_SIZE,
            expectedPath = CUSTOM_PATH
        )
    }

    @Test
    fun `check content length header intact with not-gzipped response body if network capture not enabled`() {
        preNetworkInterceptorAfterResponseSupplier = ::checkUncompressedBodySize
        server.enqueue(createBaseMockResponse().setBody(RESPONSE_BODY))
        runGetRequest()
        assertNetworkBodyNotCaptured()
    }

    @Test
    fun `check content length header intact with gzipped response body if network capture not enabled`() {
        preNetworkInterceptorAfterResponseSupplier = ::checkCompressedBodySize
        server.enqueue(createBaseMockResponse().setGzipBody(RESPONSE_BODY))
        runGetRequest()
        assertNetworkBodyNotCaptured()
    }

    @Test
    fun `check response body is not gzipped and no errors in capturing response body data when body is not gzipped`() {
        preNetworkInterceptorAfterResponseSupplier = ::checkUncompressedBodySize
        server.enqueue(createBaseMockResponse().setBody(RESPONSE_BODY))
        runAndValidatePostRequest(RESPONSE_BODY_SIZE)
    }

    @Test
    fun `check response body is not gzipped and no errors in capturing response body data when response body is gzipped`() {
        preNetworkInterceptorAfterResponseSupplier = fun(response: Response): Response {
            val responseBuilder: Response.Builder = response.newBuilder().request(response.request)
            assertNull(response.header(CONTENT_ENCODING_HEADER_NAME))
            val bodySize = response.body?.bytes()?.size
            assertEquals(RESPONSE_BODY_SIZE, bodySize)
            assertEquals(-1L, response.body?.contentLength())
            assertNull(response.header(CONTENT_LENGTH_HEADER_NAME))
            return responseBuilder.build()
        }
        server.enqueue(createBaseMockResponse().setGzipBody(RESPONSE_BODY))
        runAndValidatePostRequest(RESPONSE_BODY_GZIPPED_SIZE)
    }

    @Test
    fun `check application interceptor can handle compressed response without content-length parameter`() {
        preNetworkInterceptorAfterResponseSupplier = ::removeContentLengthFromResponse
        preNetworkInterceptorAfterResponseSupplier = ::consumeBody
        server.enqueue(createBaseMockResponse().setGzipBody(RESPONSE_BODY))
        runAndValidatePostRequest(RESPONSE_BODY_GZIPPED_SIZE)
    }

    @Test
    fun `check application interceptor can handle uncompressed response without content-length parameter`() {
        preNetworkInterceptorAfterResponseSupplier = ::removeContentLengthFromResponse
        preNetworkInterceptorAfterResponseSupplier = ::consumeBody
        server.enqueue(createBaseMockResponse().setBody(RESPONSE_BODY))
        runAndValidatePostRequest(RESPONSE_BODY_SIZE)
    }

    @Test
    fun `check network interceptor can handle compressed response without content-length parameter`() {
        postNetworkInterceptorAfterResponseSupplier = ::removeContentLengthFromResponse
        preNetworkInterceptorAfterResponseSupplier = ::consumeBody
        server.enqueue(createBaseMockResponse().setGzipBody(RESPONSE_BODY))
        runAndValidatePostRequest(RESPONSE_BODY_GZIPPED_SIZE)
    }

    @Test
    fun `check network interceptor can handle uncompressed response without content-length parameter`() {
        postNetworkInterceptorAfterResponseSupplier = ::removeContentLengthFromResponse
        preNetworkInterceptorAfterResponseSupplier = ::consumeBody
        server.enqueue(createBaseMockResponse().setBody(RESPONSE_BODY))
        runAndValidatePostRequest(RESPONSE_BODY_SIZE)
    }

    @Test
    fun `streaming requests recorded properly`() {
        postNetworkInterceptorAfterResponseSupplier = ::removeContentLengthFromResponse
        server.enqueue(
            createBaseMockResponse().addHeader(CONTENT_TYPE_HEADER_NAME, CONTENT_TYPE_EVENT_STREAM)
                .setBody(RESPONSE_BODY)
        )
        runAndValidatePostRequest(0)
    }

    @Test
    fun `exceptions with canonical name and message cause incomplete network request to be recorded with those values`() {
        preNetworkInterceptorBeforeRequestSupplier = { throw SocketException("bad bad socket") }
        assertThrows(SocketException::class.java) { runPostRequest() }
        assertNetworkRequestReceived { span ->
            val attrs = span.attributes
            assertEquals(SocketException::class.java.canonicalName, attrs[ErrorAttributes.ERROR_TYPE])
            assertEquals("bad bad socket", attrs[ExceptionAttributes.EXCEPTION_MESSAGE])
        }
    }

    @Test
    fun `anonymous exception with no message causes incomplete network request to be recorded with empty error type and message values`() {
        preNetworkInterceptorBeforeRequestSupplier = { throw object : Exception() {} }
        assertThrows(Exception::class.java) { runPostRequest() }
        assertNetworkRequestReceived { span ->
            val attrs = span.attributes
            assertEquals(UNKNOWN_EXCEPTION, attrs[ErrorAttributes.ERROR_TYPE])
            assertEquals(UNKNOWN_MESSAGE, attrs[ExceptionAttributes.EXCEPTION_MESSAGE])
        }
    }

    @Test
    fun `EmbraceCustomPathException records incomplete network request with custom path and the correct error type and message`() {
        postRequestBuilder.header("x-emb-path", CUSTOM_PATH)
        val exc = IllegalStateException("Burned")
        preNetworkInterceptorBeforeRequestSupplier = {
            throw EmbraceCustomPathException(CUSTOM_PATH, exc)
        }
        assertThrows(EmbraceCustomPathException::class.java) { runPostRequest() }
        assertNetworkRequestReceived { span ->
            val attrs = span.attributes
            assertEquals(exc::class.java.canonicalName, attrs[ErrorAttributes.ERROR_TYPE])
            assertEquals(exc.message, attrs[ExceptionAttributes.EXCEPTION_MESSAGE])
            val url = checkNotNull(attrs[UrlAttributes.URL_FULL])
            assertTrue(url.endsWith(CUSTOM_PATH))
        }
    }

    @Test
    fun `EmbraceCustomPathException with anonymous cause records request with custom path and empty error type and message`() {
        postRequestBuilder.header("x-emb-path", CUSTOM_PATH)
        preNetworkInterceptorBeforeRequestSupplier =
            { throw EmbraceCustomPathException(CUSTOM_PATH, object : Exception() {}) }
        assertThrows(EmbraceCustomPathException::class.java) { runPostRequest() }
        assertNetworkRequestReceived { span ->
            val attrs = span.attributes
            assertEquals(UNKNOWN_EXCEPTION, attrs[ErrorAttributes.ERROR_TYPE])
            assertEquals(UNKNOWN_MESSAGE, attrs[ExceptionAttributes.EXCEPTION_MESSAGE])
            val url = checkNotNull(attrs[UrlAttributes.URL_FULL])
            assertTrue(url.endsWith(CUSTOM_PATH))
        }
    }

    @Test
    fun `check traceparent not injected or forwarded by default for a complete request `() {
        server.enqueue(createBaseMockResponse())
        runPostRequest()
        assertNetworkRequestReceived { span ->
            val attrs = span.attributes
            assertEquals("200", attrs[HttpAttributes.HTTP_RESPONSE_STATUS_CODE])
            assertNull(attrs["emb.w3c_traceparent"])
        }
    }

    @Test
    fun `check w3c traceparent representation is injected and forwarded for a complete request if feature flag is on`() {
        isNetworkSpanForwardingEnabled = true
        server.enqueue(createBaseMockResponse())
        val response = runPostRequest()
        assertNetworkRequestReceived { span ->
            val attrs = span.attributes
            val traceparent = span.asW3cTraceparent()
            assertEquals("200", attrs[HttpAttributes.HTTP_RESPONSE_STATUS_CODE])
            assertEquals(traceparent, attrs["emb.w3c_traceparent"])
            assertEquals(traceparent, response.networkResponse?.request?.header(TRACEPARENT_HEADER))
        }
    }

    @Test
    fun `check traceparent is injected and forwarded for requests that don't complete because of EmbraceCustomPathException`() {
        isNetworkSpanForwardingEnabled = true
        postRequestBuilder.header("x-emb-path", CUSTOM_PATH)
        preNetworkInterceptorBeforeRequestSupplier =
            { throw EmbraceCustomPathException(CUSTOM_PATH, IllegalStateException()) }
        assertThrows(EmbraceCustomPathException::class.java) { runPostRequest() }
        assertNetworkRequestReceived { span ->
            val attrs = span.attributes
            assertNull(attrs[HttpAttributes.HTTP_RESPONSE_STATUS_CODE])
            assertEquals(span.asW3cTraceparent(), attrs["emb.w3c_traceparent"])
        }
    }

    @Test
    fun `check traceparent is injected and forwarded for incomplete requests`() {
        isNetworkSpanForwardingEnabled = true
        preNetworkInterceptorBeforeRequestSupplier = { throw NullPointerException("hell nah") }
        assertThrows(NullPointerException::class.java) { runPostRequest() }
        assertNetworkRequestReceived { span ->
            val attrs = span.attributes
            assertNull(attrs[HttpAttributes.HTTP_RESPONSE_STATUS_CODE])
            assertEquals(span.asW3cTraceparent(), attrs["emb.w3c_traceparent"])
        }
    }

    private fun assertNetworkBodyNotCaptured() {
        assertTrue(args.destination.logEvents.isEmpty())
    }

    private fun assertNetworkRequestReceived(assertions: (FakeSpanToken) -> Unit) {
        assertions(args.destination.createdSpans.single())
    }

    private fun createBaseMockResponse(httpStatus: Int = 200) =
        MockResponse()
            .setResponseCode(httpStatus)
            .addHeader(RESPONSE_HEADER_NAME, RESPONSE_HEADER_VALUE)

    private fun MockResponse.setGzipBody(stringBody: String): MockResponse =
        setBody(
            Buffer().write(
                ByteArrayOutputStream().use { byteArrayStream ->
                    GZIPOutputStream(byteArrayStream).use { gzipStream ->
                        gzipStream.write(stringBody.toByteArray())
                        gzipStream.finish()
                    }
                    byteArrayStream.toByteArray()
                }
            )
        ).addHeader(CONTENT_ENCODING_HEADER_NAME, ENCODING_GZIP)

    private fun runAndValidatePostRequest(
        expectedResponseBodySize: Int,
        expectedPath: String = DEFAULT_PATH,
        expectedHttpStatus: Int = 200,
    ) {
        args.destination.createdSpans.clear()
        validateWholeRequest(
            path = expectedPath,
            httpStatus = expectedHttpStatus,
            responseBodySize = expectedResponseBodySize,
            httpMethod = "POST",
            requestSize = REQUEST_BODY_SIZE,
            responseBody = RESPONSE_BODY,
            systemClockTimes = runAndGetResponseTimes(::runPostRequest)
        )
    }

    private fun runAndValidateGetRequest(
        expectedResponseBodySize: Int,
    ) {
        args.destination.createdSpans.clear()
        validateWholeRequest(
            path = DEFAULT_PATH,
            httpStatus = 200,
            httpMethod = "GET",
            requestSize = 0,
            responseBodySize = expectedResponseBodySize,
            responseBody = null,
            systemClockTimes = runAndGetResponseTimes(::runGetRequest)
        )
    }

    private fun runAndGetResponseTimes(action: () -> Response): SystemClockTimes {
        val beforeSystemTime = System.currentTimeMillis()
        val minClockDrift = beforeSystemTime - sdkClock.now()
        action()
        val afterSystemTime = System.currentTimeMillis()
        val maxClockDrift = afterSystemTime - sdkClock.now()
        return SystemClockTimes(
            timeBeforeRequest = beforeSystemTime,
            timeAfterRequest = afterSystemTime,
            minClockDrift = minClockDrift,
            maxClockDrift = maxClockDrift
        )
    }

    private fun runPostRequest(): Response =
        checkNotNull(okHttpClient.newCall(postRequestBuilder.build()).execute())

    private fun runGetRequest(): Response =
        checkNotNull(okHttpClient.newCall(getRequestBuilder.build()).execute())

    @Suppress("LongParameterList")
    private fun validateWholeRequest(
        path: String,
        httpMethod: String,
        httpStatus: Int,
        requestSize: Int,
        responseBodySize: Int,
        errorType: String? = null,
        errorMessage: String? = null,
        traceId: String? = null,
        w3cTraceparent: String? = null,
        responseBody: String?,
        systemClockTimes: SystemClockTimes,
    ) {
        assertNetworkRequestReceived { span ->
            assertTrue(
                "SystemClock before: ${systemClockTimes.timeBeforeRequest}, " +
                    "max drift: ${systemClockTimes.maxClockDrift}, span start time: ${span.startTimeMs}",
                systemClockTimes.timeBeforeRequest - systemClockTimes.maxClockDrift <= span.startTimeMs
            )
            val endTime = checkNotNull(span.endTimeMs)
            assertTrue(
                "SystemClock after: ${systemClockTimes.timeAfterRequest}, " +
                    "min drift: ${systemClockTimes.minClockDrift}, span end time: $endTime",
                systemClockTimes.timeAfterRequest - systemClockTimes.minClockDrift >= endTime
            )
            val attrs = span.attributes
            assertEquals(server.url(path).toString(), attrs[UrlAttributes.URL_FULL])
            assertEquals(httpMethod, attrs[HttpAttributes.HTTP_REQUEST_METHOD])
            assertEquals(httpStatus.toString(), attrs[HttpAttributes.HTTP_RESPONSE_STATUS_CODE])
            assertEquals(requestSize.toString(), attrs[HttpAttributes.HTTP_REQUEST_BODY_SIZE])
            assertEquals(responseBodySize.toString(), attrs[HttpAttributes.HTTP_RESPONSE_BODY_SIZE])
            assertEquals(errorType, attrs[ErrorAttributes.ERROR_TYPE])
            assertEquals(errorMessage, attrs[ExceptionAttributes.EXCEPTION_MESSAGE])
            assertEquals(w3cTraceparent, attrs["emb.w3c_traceparent"])
            assertEquals(getValidTraceId(traceId), attrs["emb.trace_id"])

            if (responseBody != null) {
                validateNetworkCaptureData(responseBody)
            }
        }
    }

    private fun validateNetworkCaptureData(responseBody: String) {
        val log = args.destination.logEvents.single()
        assertTrue(log.schemaType is SchemaType.NetworkCapturedRequest)
        val attrs = log.schemaType.attributes()
        validateDefaultNonBodyNetworkCaptureData(log)
        assertEquals(responseBody, attrs["response-body"])
        assertNull(attrs[ExceptionAttributes.EXCEPTION_MESSAGE])
    }

    private fun validateDefaultNonBodyNetworkCaptureData(log: FakeLogData) {
        val attrs = log.schemaType.attributes()
        val reqHeader = checkNotNull(attrs[HttpAttributes.HTTP_REQUEST_HEADER])
        assertTrue(reqHeader.contains("requestheader=requestHeaderVal"))

        val responseHeader = checkNotNull(attrs[HttpAttributes.HTTP_RESPONSE_HEADER])
        assertTrue(responseHeader.contains("responseheader=responseHeaderVal"))
        assertEquals(DEFAULT_QUERY_STRING, attrs["request-query"])

        val body = checkNotNull(attrs["request-body"])
        assertEquals(REQUEST_BODY_STRING, body)
    }

    private fun checkUncompressedBodySize(response: Response) =
        checkBodySize(response, RESPONSE_BODY_SIZE, false)

    private fun checkCompressedBodySize(response: Response) =
        checkBodySize(response, RESPONSE_BODY_GZIPPED_SIZE, true)

    private fun checkBodySize(
        response: Response,
        expectedSize: Int,
        compressed: Boolean,
    ): Response {
        val responseBuilder: Response.Builder = response.newBuilder().request(response.request)
        if (compressed) {
            assertEquals(ENCODING_GZIP, response.header(CONTENT_ENCODING_HEADER_NAME))
        } else {
            assertNull(response.header(CONTENT_ENCODING_HEADER_NAME))
        }
        val bodySize = response.body?.bytes()?.size
        assertEquals(expectedSize, bodySize)
        assertEquals(expectedSize.toLong(), response.body?.contentLength())
        assertEquals(expectedSize.toString(), response.header(CONTENT_LENGTH_HEADER_NAME))
        return responseBuilder.build()
    }

    private fun removeContentLengthFromResponse(response: Response): Response {
        val responseBuilder: Response.Builder = response.newBuilder().request(response.request)
        val newHeaders: Headers = response.headers.newBuilder()
            .removeAll(CONTENT_LENGTH_HEADER_NAME)
            .build()
        responseBuilder.headers(newHeaders)
        return responseBuilder.build()
    }

    private fun consumeBody(response: Response): Response {
        checkNotNull(response.body).bytes()
        return response
    }

    private data class SystemClockTimes(
        val timeBeforeRequest: Long,
        val timeAfterRequest: Long,
        val minClockDrift: Long,
        val maxClockDrift: Long,
    )
}
