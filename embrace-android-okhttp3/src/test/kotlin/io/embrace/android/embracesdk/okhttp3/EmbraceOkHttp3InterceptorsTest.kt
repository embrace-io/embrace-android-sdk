package io.embrace.android.embracesdk.okhttp3

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.okhttp3.EmbraceOkHttp3ApplicationInterceptor.Companion.UNKNOWN_EXCEPTION
import io.embrace.android.embracesdk.okhttp3.EmbraceOkHttp3ApplicationInterceptor.Companion.UNKNOWN_MESSAGE
import io.embrace.android.embracesdk.okhttp3.EmbraceOkHttp3ApplicationInterceptor.Companion.causeMessage
import io.embrace.android.embracesdk.okhttp3.EmbraceOkHttp3ApplicationInterceptor.Companion.causeName
import io.embrace.android.embracesdk.okhttp3.EmbraceOkHttp3NetworkInterceptor.Companion.CONTENT_ENCODING_HEADER_NAME
import io.embrace.android.embracesdk.okhttp3.EmbraceOkHttp3NetworkInterceptor.Companion.CONTENT_LENGTH_HEADER_NAME
import io.embrace.android.embracesdk.okhttp3.EmbraceOkHttp3NetworkInterceptor.Companion.CONTENT_TYPE_EVENT_STREAM
import io.embrace.android.embracesdk.okhttp3.EmbraceOkHttp3NetworkInterceptor.Companion.CONTENT_TYPE_HEADER_NAME
import io.embrace.android.embracesdk.okhttp3.EmbraceOkHttp3NetworkInterceptor.Companion.ENCODING_GZIP
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
import java.io.ByteArrayOutputStream
import java.net.SocketException
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream

internal class EmbraceOkHttp3InterceptorsTest {

    companion object {
        private const val requestHeaderName = "requestHeader"
        private const val requestHeaderValue = "requestHeaderVal"
        private const val defaultQueryString = "param=yesPlease"
        private const val defaultPath = "/test/default-path"
        private const val customPath = "/test/custom-path"
        private const val requestBodyString = "hey body"
        private const val requestBodySize = 8
        private const val responseHeaderName = "responseHeader"
        private const val responseBody = "{\"bodyString\" = \"stringstringstringstringstringstringstringstringstringstringstringstring\"}"
        private const val responseBodySize = 91
        private const val responseBodyGzippedSize = 43
        private const val responseHeaderValue = "responseHeaderVal"
        private const val TRACEPARENT_HEADER = "traceparent"
        private const val CUSTOM_TRACEPARENT = "00-b583a45b2c7c813e0ebc6aa0835b9d98-b5475c618bb98e67-01"
        private const val GENERATED_TRACEPARENT = "00-3c72a77a7b51af6fb3778c06d4c165ce-4c1d710fffc88e35-01"
        private const val FAKE_SDK_TIME = 1692201601000L
        private const val CLOCK_DRIFT = 5000L
        private const val FAKE_SYSTEM_TIME = FAKE_SDK_TIME + CLOCK_DRIFT
    }

    private lateinit var server: MockWebServer
    private lateinit var applicationInterceptor: EmbraceOkHttp3ApplicationInterceptor
    private lateinit var networkInterceptor: EmbraceOkHttp3NetworkInterceptor
    private lateinit var preNetworkInterceptorTestInterceptor: Interceptor
    private lateinit var postNetworkInterceptorTestInterceptor: Interceptor
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var mockEmbrace: Embrace
    private lateinit var mockInternalInterface: EmbraceInternalInterface
    private lateinit var getRequestBuilder: Request.Builder
    private lateinit var postRequestBuilder: Request.Builder
    private lateinit var capturedEmbraceNetworkRequest: CapturingSlot<EmbraceNetworkRequest>
    private lateinit var mockSystemClock: Clock
    private var preNetworkInterceptorBeforeRequestSupplier: (Request) -> Request = { request -> request }
    private var preNetworkInterceptorAfterResponseSupplier: (Response) -> Response = { response -> response }
    private var postNetworkInterceptorBeforeRequestSupplier: (Request) -> Request = { request -> request }
    private var postNetworkInterceptorAfterResponseSupplier: (Response) -> Response = { response -> response }
    private var isSDKStarted = true
    private var isNetworkCaptureDisabled = false
    private var isNetworkSpanForwardingEnabled = false

    @Before
    fun setup() {
        server = MockWebServer()
        mockEmbrace = mockk(relaxed = true)
        mockInternalInterface = mockk(relaxed = true)
        every { mockInternalInterface.shouldCaptureNetworkBody(any(), "POST") } answers { true }
        every { mockInternalInterface.shouldCaptureNetworkBody(any(), "GET") } answers { false }
        every { mockInternalInterface.isNetworkSpanForwardingEnabled() } answers { isNetworkSpanForwardingEnabled }
        every { mockInternalInterface.isInternalNetworkCaptureDisabled() } answers { isNetworkCaptureDisabled }
        every { mockInternalInterface.getSdkCurrentTime() } answers { FAKE_SDK_TIME }
        applicationInterceptor = EmbraceOkHttp3ApplicationInterceptor(mockEmbrace)
        preNetworkInterceptorTestInterceptor = TestInspectionInterceptor(
            beforeRequestSent = { request -> preNetworkInterceptorBeforeRequestSupplier.invoke(request) },
            afterResponseReceived = { response -> preNetworkInterceptorAfterResponseSupplier.invoke(response) }
        )
        mockSystemClock = mockk(relaxed = true)
        every { mockSystemClock.now() } answers { FAKE_SYSTEM_TIME }
        networkInterceptor = EmbraceOkHttp3NetworkInterceptor(mockEmbrace, mockSystemClock)
        postNetworkInterceptorTestInterceptor = TestInspectionInterceptor(
            beforeRequestSent = { request -> postNetworkInterceptorBeforeRequestSupplier.invoke(request) },
            afterResponseReceived = { response -> postNetworkInterceptorAfterResponseSupplier.invoke(response) }
        )
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(applicationInterceptor)
            .addNetworkInterceptor(preNetworkInterceptorTestInterceptor)
            .addNetworkInterceptor(networkInterceptor)
            .addNetworkInterceptor(postNetworkInterceptorTestInterceptor)
            .build()
        getRequestBuilder = Request.Builder()
            .url(server.url("$defaultPath?$defaultQueryString"))
            .get()
            .header(requestHeaderName, requestHeaderValue)
        postRequestBuilder = Request.Builder()
            .url(server.url("$defaultPath?$defaultQueryString"))
            .post(requestBodyString.toRequestBody())
            .header(requestHeaderName, requestHeaderValue)
        capturedEmbraceNetworkRequest = slot()
        every { mockEmbrace.isStarted } answers { isSDKStarted }
        every { mockEmbrace.recordNetworkRequest(capture(capturedEmbraceNetworkRequest)) } answers { }
        every { mockEmbrace.generateW3cTraceparent() } answers { GENERATED_TRACEPARENT }
        every { mockEmbrace.internalInterface } answers { mockInternalInterface }
        isSDKStarted = true
        isNetworkCaptureDisabled = false
        isNetworkSpanForwardingEnabled = false
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `completed successful requests with uncompressed responses are recorded properly`() {
        preNetworkInterceptorAfterResponseSupplier = ::consumeBody
        server.enqueue(createBaseMockResponse().setBody(responseBody))
        runAndValidatePostRequest(responseBodySize)

        server.enqueue(createBaseMockResponse().setBody(responseBody))
        runAndValidateGetRequest(responseBodySize)
    }

    @Test
    fun `completed successful requests with gzipped responses are recorded properly`() {
        preNetworkInterceptorAfterResponseSupplier = ::consumeBody
        server.enqueue(createBaseMockResponse().setGzipBody(responseBody))
        runAndValidatePostRequest(responseBodyGzippedSize)

        server.enqueue(createBaseMockResponse().setGzipBody(responseBody))
        runAndValidateGetRequest(responseBodyGzippedSize)
    }

    @Test
    fun `completed unsuccessful requests are recorded properly`() {
        server.enqueue(createBaseMockResponse(500).setGzipBody(responseBody))
        runAndValidatePostRequest(expectedResponseBodySize = responseBodyGzippedSize, expectedHttpStatus = 500)
    }

    @Test
    fun `completed requests with custom paths are recorded properly`() {
        server.enqueue(createBaseMockResponse().setGzipBody(responseBody))
        postRequestBuilder.header("x-emb-path", customPath)
        runAndValidatePostRequest(expectedResponseBodySize = responseBodyGzippedSize, expectedPath = customPath)
    }

    @Test
    fun `incomplete requests with custom paths are recorded properly`() {
        server.enqueue(createBaseMockResponse().setGzipBody(responseBody))
        postRequestBuilder.header("x-emb-path", customPath)
        runAndValidatePostRequest(expectedResponseBodySize = responseBodyGzippedSize, expectedPath = customPath)
    }

    @Test
    fun `completed requests are not recorded if the SDK has not started`() {
        isSDKStarted = false
        server.enqueue(createBaseMockResponse())
        runGetRequest()
        server.enqueue(createBaseMockResponse())
        runPostRequest()
        verify(exactly = 0) { mockEmbrace.recordNetworkRequest(any()) }
    }

    @Test
    fun `completed requests are not recorded if network capture has been disabled internally`() {
        isNetworkCaptureDisabled = true
        server.enqueue(createBaseMockResponse())
        runGetRequest()
        server.enqueue(createBaseMockResponse())
        runPostRequest()
        verify(exactly = 0) { mockEmbrace.recordNetworkRequest(any()) }
    }

    @Test
    fun `incomplete requests are not recorded if the SDK has not started`() {
        isSDKStarted = false
        preNetworkInterceptorBeforeRequestSupplier = { throw SocketException() }
        assertThrows(SocketException::class.java) { runGetRequest() }
        postRequestBuilder.header("x-emb-path", customPath)
        preNetworkInterceptorBeforeRequestSupplier = { throw EmbraceCustomPathException(customPath, SocketException()) }
        assertThrows(EmbraceCustomPathException::class.java) { runPostRequest() }
        verify(exactly = 0) { mockEmbrace.recordNetworkRequest(any()) }
    }

    @Test
    fun `EmbraceOkHttp3NetworkInterceptor does nothing if SDK not started`() {
        isSDKStarted = false
        server.enqueue(createBaseMockResponse())
        runGetRequest()
        verify(exactly = 0) { mockInternalInterface.isNetworkSpanForwardingEnabled() }
        verify(exactly = 0) { mockInternalInterface.shouldCaptureNetworkBody(any(), any()) }
    }

    @Test
    fun `check content length header intact with not-gzipped response body if network capture not enabled`() {
        preNetworkInterceptorAfterResponseSupplier = ::checkUncompressedBodySize
        server.enqueue(createBaseMockResponse().setBody(responseBody))
        runGetRequest()
        assertNull(capturedEmbraceNetworkRequest.captured.networkCaptureData)
    }

    @Test
    fun `check content length header intact with gzipped response body if network capture not enabled`() {
        preNetworkInterceptorAfterResponseSupplier = ::checkCompressedBodySize
        server.enqueue(createBaseMockResponse().setGzipBody(responseBody))
        runGetRequest()
        assertNull(capturedEmbraceNetworkRequest.captured.networkCaptureData)
    }

    @Test
    fun `check response body is not gzipped and no errors in capturing response body data when body is not gzipped`() {
        preNetworkInterceptorAfterResponseSupplier = ::checkUncompressedBodySize
        server.enqueue(createBaseMockResponse().setBody(responseBody))
        runAndValidatePostRequest(responseBodySize)
    }

    @Test
    fun `check response body is not gzipped and no errors in capturing response body data when response body is gzipped`() {
        preNetworkInterceptorAfterResponseSupplier = fun(response: Response): Response {
            val responseBuilder: Response.Builder = response.newBuilder().request(response.request)
            assertNull(response.header(CONTENT_ENCODING_HEADER_NAME))
            val bodySize = response.body?.bytes()?.size
            assertEquals(responseBodySize, bodySize)
            assertEquals(-1L, response.body?.contentLength())
            assertNull(response.header(CONTENT_LENGTH_HEADER_NAME))
            return responseBuilder.build()
        }
        server.enqueue(createBaseMockResponse().setGzipBody(responseBody))
        runAndValidatePostRequest(responseBodyGzippedSize)
    }

    @Test
    fun `check error when getting response body in network capture`() {
        val bodyThatKills: Buffer = mockk(relaxed = true)
        every { bodyThatKills.size } answers { 10 }
        server.enqueue(createBaseMockResponse().setBody(bodyThatKills))
        runPostRequest()
        val networkCaptureData = checkNotNull(capturedEmbraceNetworkRequest.captured.networkCaptureData)
        with(networkCaptureData) {
            validateDefaultNonBodyNetworkCaptureData(this)
            assertTrue(checkNotNull(dataCaptureErrorMessage).contains("Response Body"))
        }
    }

    @Test
    fun `check EmbraceOkHttp3ApplicationInterceptor can handle compressed response without content-length parameter`() {
        preNetworkInterceptorAfterResponseSupplier = ::removeContentLengthFromResponse
        preNetworkInterceptorAfterResponseSupplier = ::consumeBody
        server.enqueue(createBaseMockResponse().setGzipBody(responseBody))
        runAndValidatePostRequest(responseBodyGzippedSize)
    }

    @Test
    fun `check EmbraceOkHttp3ApplicationInterceptor can handle uncompressed response without content-length parameter`() {
        preNetworkInterceptorAfterResponseSupplier = ::removeContentLengthFromResponse
        preNetworkInterceptorAfterResponseSupplier = ::consumeBody
        server.enqueue(createBaseMockResponse().setBody(responseBody))
        runAndValidatePostRequest(responseBodySize)
    }

    @Test
    fun `check EmbraceOkHttp3NetworkInterceptor can handle compressed response without content-length parameter`() {
        postNetworkInterceptorAfterResponseSupplier = ::removeContentLengthFromResponse
        preNetworkInterceptorAfterResponseSupplier = ::consumeBody
        server.enqueue(createBaseMockResponse().setGzipBody(responseBody))
        runAndValidatePostRequest(responseBodyGzippedSize)
    }

    @Test
    fun `check EmbraceOkHttp3NetworkInterceptor can handle uncompressed response without content-length parameter`() {
        postNetworkInterceptorAfterResponseSupplier = ::removeContentLengthFromResponse
        preNetworkInterceptorAfterResponseSupplier = ::consumeBody
        server.enqueue(createBaseMockResponse().setBody(responseBody))
        runAndValidatePostRequest(responseBodySize)
    }

    @Test
    fun `streaming requests recorded properly`() {
        postNetworkInterceptorAfterResponseSupplier = ::removeContentLengthFromResponse
        server.enqueue(createBaseMockResponse().addHeader(CONTENT_TYPE_HEADER_NAME, CONTENT_TYPE_EVENT_STREAM).setBody(responseBody))
        runAndValidatePostRequest(0)
    }

    @Test
    fun `exceptions with canonical name and message cause incomplete network request to be recorded with those values`() {
        preNetworkInterceptorBeforeRequestSupplier = { throw SocketException("bad bad socket") }
        assertThrows(SocketException::class.java) { runPostRequest() }
        with(capturedEmbraceNetworkRequest.captured) {
            assertEquals(SocketException::class.java.canonicalName, errorType)
            assertEquals("bad bad socket", errorMessage)
        }
    }

    @Test
    fun `anonymous exception with no message causes incomplete network request to be recorded with empty error type and message values`() {
        preNetworkInterceptorBeforeRequestSupplier = { throw object : Exception() {} }
        assertThrows(Exception::class.java) { runPostRequest() }
        with(capturedEmbraceNetworkRequest.captured) {
            assertEquals(UNKNOWN_EXCEPTION, errorType)
            assertEquals(UNKNOWN_MESSAGE, errorMessage)
        }
    }

    @Test
    fun `EmbraceCustomPathException records incomplete network request with custom path and the correct error type and message`() {
        postRequestBuilder.header("x-emb-path", customPath)
        preNetworkInterceptorBeforeRequestSupplier = {
            throw EmbraceCustomPathException(customPath, IllegalStateException("Burned"))
        }
        assertThrows(EmbraceCustomPathException::class.java) { runPostRequest() }
        with(capturedEmbraceNetworkRequest.captured) {
            assertEquals(IllegalStateException::class.java.canonicalName, errorType)
            assertEquals("Burned", errorMessage)
            assertTrue(url.endsWith("$customPath?$defaultQueryString"))
        }
    }

    @Test
    fun `EmbraceCustomPathException with anonymous cause records request with custom path and empty error type and message`() {
        postRequestBuilder.header("x-emb-path", customPath)
        preNetworkInterceptorBeforeRequestSupplier = { throw EmbraceCustomPathException(customPath, object : Exception() {}) }
        assertThrows(EmbraceCustomPathException::class.java) { runPostRequest() }
        with(capturedEmbraceNetworkRequest.captured) {
            assertEquals(UNKNOWN_EXCEPTION, errorType)
            assertEquals(UNKNOWN_MESSAGE, errorMessage)
            assertTrue(url.endsWith("$customPath?$defaultQueryString"))
        }
    }

    @Test
    fun `check traceparent not injected or forwarded by default for a complete request `() {
        server.enqueue(createBaseMockResponse())
        runPostRequest()
        assertEquals(200, capturedEmbraceNetworkRequest.captured.responseCode)
        assertNull(capturedEmbraceNetworkRequest.captured.w3cTraceparent)
    }

    @Test
    fun `check existing traceparent not forwarded by default for a complete request`() {
        server.enqueue(createBaseMockResponse())
        postRequestBuilder.header(TRACEPARENT_HEADER, CUSTOM_TRACEPARENT)
        runPostRequest()
        assertEquals(200, capturedEmbraceNetworkRequest.captured.responseCode)
        assertNull(capturedEmbraceNetworkRequest.captured.w3cTraceparent)
    }

    @Test
    fun `check traceparent injected and forwarded for a complete request if feature flag is on`() {
        isNetworkSpanForwardingEnabled = true
        server.enqueue(createBaseMockResponse())
        runPostRequest()
        assertEquals(200, capturedEmbraceNetworkRequest.captured.responseCode)
        assertEquals(GENERATED_TRACEPARENT, capturedEmbraceNetworkRequest.captured.w3cTraceparent)
    }

    @Test
    fun `check existing traceparent is forwarded for a complete request`() {
        isNetworkSpanForwardingEnabled = true
        server.enqueue(createBaseMockResponse())
        postRequestBuilder.header(TRACEPARENT_HEADER, CUSTOM_TRACEPARENT)
        runPostRequest()
        assertEquals(200, capturedEmbraceNetworkRequest.captured.responseCode)
        assertEquals(CUSTOM_TRACEPARENT, capturedEmbraceNetworkRequest.captured.w3cTraceparent)
    }

    @Test
    fun `check traceparent not injected and forwarded for requests that don't complete because of EmbraceCustomPathException`() {
        isNetworkSpanForwardingEnabled = true
        postRequestBuilder.header("x-emb-path", customPath)
        preNetworkInterceptorBeforeRequestSupplier = { throw EmbraceCustomPathException(customPath, IllegalStateException()) }
        assertThrows(EmbraceCustomPathException::class.java) { runPostRequest() }
        assertNull(capturedEmbraceNetworkRequest.captured.responseCode)
        assertNull(capturedEmbraceNetworkRequest.captured.w3cTraceparent)
    }

    @Test
    fun `check traceparent not injected and forwarded for incomplete requests`() {
        isNetworkSpanForwardingEnabled = true
        preNetworkInterceptorBeforeRequestSupplier = { throw NullPointerException("hell nah") }
        assertThrows(NullPointerException::class.java) { runPostRequest() }
        assertNull(capturedEmbraceNetworkRequest.captured.responseCode)
        assertNull(capturedEmbraceNetworkRequest.captured.w3cTraceparent)
    }

    @Test
    fun `check existing traceparent forwarded for requests that don't complete because of EmbraceCustomPathException`() {
        isNetworkSpanForwardingEnabled = true
        postRequestBuilder.header("x-emb-path", customPath).header(TRACEPARENT_HEADER, CUSTOM_TRACEPARENT)
        preNetworkInterceptorBeforeRequestSupplier = { throw EmbraceCustomPathException(customPath, IllegalStateException()) }
        assertThrows(EmbraceCustomPathException::class.java) { runPostRequest() }
        assertNull(capturedEmbraceNetworkRequest.captured.responseCode)
        assertEquals(CUSTOM_TRACEPARENT, capturedEmbraceNetworkRequest.captured.w3cTraceparent)
    }

    @Test
    fun `check existing traceparent forwarded incomplete requests`() {
        isNetworkSpanForwardingEnabled = true
        postRequestBuilder.header(TRACEPARENT_HEADER, CUSTOM_TRACEPARENT)
        preNetworkInterceptorBeforeRequestSupplier = { throw SocketException("hell nah") }
        assertThrows(SocketException::class.java) { runPostRequest() }
        assertNull(capturedEmbraceNetworkRequest.captured.responseCode)
        assertEquals(CUSTOM_TRACEPARENT, capturedEmbraceNetworkRequest.captured.w3cTraceparent)
    }

    @Test
    fun `test throwableName`() {
        assertEquals("name should be empty string if the Throwable is null", causeName(null), "")
        assertEquals(
            "name should be empty string if the Throwable's cause is null",
            causeName(RuntimeException("message", null)),
            ""
        )
        assertEquals(
            "name is unexpected",
            causeName(
                RuntimeException("message", IllegalArgumentException())
            ),
            IllegalArgumentException::class.qualifiedName
        )
    }

    @Test
    fun `test throwableMessage`() {
        assertEquals(
            "message should be empty string if Throwable is null",
            causeMessage(null),
            ""
        )
        assertEquals(
            "message should be empty string if the Throwable's cause is null",
            causeMessage(RuntimeException("message", null)),
            ""
        )
        assertEquals(
            "message should be empty string if the Throwable's cause's message is null",
            causeMessage(RuntimeException("message", IllegalArgumentException())),
            ""
        )
        val message = "this is a message"
        assertEquals(
            "message is unexpected",
            causeMessage(RuntimeException("message", IllegalArgumentException(message))),
            message
        )
    }

    @Test
    fun `check consistent offsets produce expected start and end times`() {
        val clockDrifts = listOf(-500L, -1L, 0L, 1L, 500L)
        clockDrifts.forEach { clockDrift ->
            runAndValidateTimestamps(
                clockDrift = clockDrift,
                extraDrift = 0L
            )
        }
    }

    @Test
    fun `check tick overs round to the lowest absolute value for the offset`() {
        val clockDrifts = listOf(-500L, -2L, -1L, 0L, 1L, 2L, 500L)
        val extraDrifts = listOf(-1L, 1L)
        clockDrifts.forEach { clockDrift ->
            extraDrifts.forEach { extraDrift ->
                runAndValidateTimestamps(
                    clockDrift = clockDrift,
                    extraDrift = extraDrift,
                )
            }
        }
    }

    @Test
    fun `check big differences in offset samples will result in no offset being used`() {
        val clockDrifts = listOf(-500L, -1L, 0L, 1L, 500L)
        val extraDrifts = listOf(-200L, -2L, 2L, 200L)
        clockDrifts.forEach { clockDrift ->
            extraDrifts.forEach { extraDrift ->
                runAndValidateTimestamps(
                    clockDrift = clockDrift,
                    extraDrift = extraDrift,
                    expectedOffset = 0L
                )
            }
        }
    }

    private fun createBaseMockResponse(httpStatus: Int = 200) =
        MockResponse()
            .setResponseCode(httpStatus)
            .addHeader(responseHeaderName, responseHeaderValue)

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
        expectedPath: String = defaultPath,
        expectedHttpStatus: Int = 200
    ) {
        val realSystemClockStartTime = System.currentTimeMillis()
        runPostRequest()
        val realSystemClockEndTime = System.currentTimeMillis()
        validateWholeRequest(
            path = expectedPath,
            httpStatus = expectedHttpStatus,
            responseBodySize = expectedResponseBodySize,
            httpMethod = "POST",
            requestSize = requestBodySize,
            responseBody = responseBody,
            realSystemClockStartTime = realSystemClockStartTime,
            realSystemClockEndTime = realSystemClockEndTime
        )
    }

    private fun runAndValidateGetRequest(
        expectedResponseBodySize: Int
    ) {
        val realSystemClockStartTime = System.currentTimeMillis()
        runGetRequest()
        val realSystemClockEndTime = System.currentTimeMillis()
        validateWholeRequest(
            path = defaultPath,
            httpStatus = 200,
            httpMethod = "GET",
            requestSize = 0,
            responseBodySize = expectedResponseBodySize,
            responseBody = null,
            realSystemClockStartTime = realSystemClockStartTime,
            realSystemClockEndTime = realSystemClockEndTime
        )
    }

    private fun runAndValidateTimestamps(
        clockDrift: Long,
        extraDrift: Long = 0L,
        expectedOffset: Long = ((clockDrift * 2) + extraDrift) / 2L
    ) {
        val realDrift = AtomicLong(clockDrift)
        every { mockSystemClock.now() } answers { FAKE_SDK_TIME + realDrift.getAndAdd(extraDrift) }
        server.enqueue(createBaseMockResponse().setBody(responseBody))
        val response = runGetRequest()
        val realSystemClockStartTime = response.sentRequestAtMillis
        val realSystemClockEndTime = response.receivedResponseAtMillis
        with(capturedEmbraceNetworkRequest) {
            assertEquals(
                "Unexpected start time when clock drifts are $clockDrift and ${clockDrift + extraDrift}:\n" +
                    "Unadjusted time: $realSystemClockStartTime with expected offset $expectedOffset\n" +
                    "Expected time: ${realSystemClockStartTime - expectedOffset}\n" +
                    "Captured time: ${captured.startTime}",
                realSystemClockStartTime - expectedOffset,
                captured.startTime
            )
            assertEquals(
                "Unexpected end time when clock drifts are $clockDrift and ${clockDrift + extraDrift}\n" +
                    "Unadjusted time: $realSystemClockEndTime with expected offset $expectedOffset\n" +
                    "Expected time: ${realSystemClockEndTime - expectedOffset}\n" +
                    "Captured time: ${captured.endTime}",
                realSystemClockEndTime - expectedOffset,
                captured.endTime
            )
        }
    }

    private fun runPostRequest(): Response = checkNotNull(okHttpClient.newCall(postRequestBuilder.build()).execute())

    private fun runGetRequest(): Response = checkNotNull(okHttpClient.newCall(getRequestBuilder.build()).execute())

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
        realSystemClockStartTime: Long,
        realSystemClockEndTime: Long
    ) {
        with(capturedEmbraceNetworkRequest) {
            assertTrue(captured.url.endsWith("$path?$defaultQueryString"))
            assertEquals(httpMethod, captured.httpMethod)
            assertTrue(realSystemClockStartTime - CLOCK_DRIFT <= captured.startTime)
            assertTrue(realSystemClockStartTime > captured.startTime)
            assertTrue(realSystemClockEndTime - CLOCK_DRIFT >= captured.endTime)
            assertTrue(realSystemClockEndTime > captured.endTime)
            assertEquals(httpStatus, captured.responseCode)
            assertEquals(requestSize.toLong(), captured.bytesOut)
            assertEquals(responseBodySize.toLong(), captured.bytesIn)
            assertEquals(errorType, captured.errorType)
            assertEquals(errorMessage, captured.errorMessage)
            assertEquals(traceId, captured.traceId)
            assertEquals(w3cTraceparent, captured.w3cTraceparent)
            if (responseBody != null) {
                validateNetworkCaptureData(responseBody)
            }
        }
    }

    private fun validateNetworkCaptureData(responseBody: String) {
        with(checkNotNull(capturedEmbraceNetworkRequest.captured.networkCaptureData)) {
            validateDefaultNonBodyNetworkCaptureData(this)
            assertEquals(responseBody, capturedResponseBody?.toResponseBody()?.string())
            assertNull(dataCaptureErrorMessage)
        }
    }

    private fun validateDefaultNonBodyNetworkCaptureData(networkCaptureData: NetworkCaptureData?) {
        with(checkNotNull(networkCaptureData)) {
            assertEquals(requestHeaderValue, requestHeaders?.get(requestHeaderName.toLowerCase()))
            assertEquals(responseHeaderValue, responseHeaders?.get(responseHeaderName.toLowerCase()))
            assertEquals(defaultQueryString, requestQueryParams)
            val buffer = Buffer()
            capturedRequestBody?.toRequestBody()?.writeTo(buffer)
            assertEquals(requestBodyString, buffer.readUtf8())
        }
    }

    private fun checkUncompressedBodySize(response: Response) = checkBodySize(response, responseBodySize, false)

    private fun checkCompressedBodySize(response: Response) = checkBodySize(response, responseBodyGzippedSize, true)

    private fun checkBodySize(response: Response, expectedSize: Int, compressed: Boolean): Response {
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
}
