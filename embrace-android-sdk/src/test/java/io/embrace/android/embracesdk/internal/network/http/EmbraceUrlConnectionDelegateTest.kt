package io.embrace.android.embracesdk.internal.network.http

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.config.behavior.NetworkBehavior.Companion.CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE
import io.embrace.android.embracesdk.config.behavior.NetworkSpanForwardingBehavior.Companion.TRACEPARENT_HEADER_NAME
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.network.http.EmbraceHttpPathOverride.PATH_OVERRIDE
import io.embrace.android.embracesdk.internal.network.http.EmbraceUrlConnectionDelegate.CONTENT_ENCODING
import io.embrace.android.embracesdk.internal.network.http.EmbraceUrlConnectionDelegate.CONTENT_LENGTH
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.concurrent.TimeoutException
import java.util.zip.GZIPOutputStream
import javax.net.ssl.HttpsURLConnection

internal class EmbraceUrlConnectionDelegateTest {

    private lateinit var mockEmbrace: Embrace
    private lateinit var mockInternalInterface: EmbraceInternalInterface
    private lateinit var capturedCallId: MutableList<String>
    private lateinit var capturedEmbraceNetworkRequest: CapturingSlot<EmbraceNetworkRequest>
    private var fakeTimeMs = REQUEST_TIME
    private var isSDKStarted = false
    private var shouldCaptureNetworkBody = true
    private var isNetworkSpanForwardingEnabled = false
    private var traceIdHeaderName = CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE

    @Before
    fun setup() {
        mockEmbrace = mockk(relaxed = true)
        every { mockEmbrace.internalInterface } answers { mockInternalInterface }
        every { mockEmbrace.isStarted } answers { isSDKStarted }
        every { mockEmbrace.traceIdHeader } answers { traceIdHeaderName }
        fakeTimeMs = REQUEST_TIME
        isSDKStarted = true
        shouldCaptureNetworkBody = true
        isNetworkSpanForwardingEnabled = false
        traceIdHeaderName = CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE
        capturedCallId = mutableListOf()
        capturedEmbraceNetworkRequest = slot()
        mockInternalInterface = mockk(relaxed = true)
        every { mockInternalInterface.shouldCaptureNetworkBody(any(), any()) } answers { shouldCaptureNetworkBody }
        every {
            mockInternalInterface.recordNetworkRequest(capture(capturedEmbraceNetworkRequest))
        } answers { }
        every { mockInternalInterface.isNetworkSpanForwardingEnabled() } answers { isNetworkSpanForwardingEnabled }
        every { mockInternalInterface.getSdkCurrentTime() } answers { fakeTimeMs }
    }

    @Test
    fun `completed successful requests with compressed responses from a wrapped stream are recorded properly`() {
        executeRequest(
            connection = createMockGzipConnection(),
            wrappedIoStream = true
        )
        validateWholeRequest(
            url = url.toString(),
            startTime = REQUEST_TIME,
            endTime = REQUEST_TIME,
            httpMethod = HttpMethod.POST.name,
            httpStatus = HTTP_OK,
            responseBodySize = gzippedResponseBodySize,
            requestSize = requestBodySize,
            networkDataCaptured = true,
            responseBody = responseBodyText
        )
    }

    @Test
    fun `completed successful requests with uncompressed responses from a wrapped stream are recorded properly`() {
        executeRequest(
            connection = createMockUncompressedConnection(),
            wrappedIoStream = true
        )
        validateWholeRequest(
            url = url.toString(),
            startTime = REQUEST_TIME,
            endTime = REQUEST_TIME,
            httpMethod = HttpMethod.POST.name,
            httpStatus = HTTP_OK,
            responseBodySize = responseBodySize,
            requestSize = requestBodySize,
            networkDataCaptured = true,
            responseBody = responseBodyText
        )
    }

    @Test
    fun `completed successful requests with compressed responses from an unwrapped output streams are recorded properly`() {
        executeRequest(
            connection = createMockGzipConnection(),
            wrappedIoStream = false
        )
        validateWholeRequest(
            url = url.toString(),
            startTime = REQUEST_TIME,
            endTime = REQUEST_TIME,
            httpMethod = HttpMethod.POST.name,
            httpStatus = HTTP_OK,
            responseBodySize = gzippedResponseBodySize,
            requestSize = 0
        )
    }

    @Test
    fun `completed successful requests with uncompressed responses from an unwrapped output streams are recorded properly`() {
        executeRequest(
            connection = createMockUncompressedConnection(),
            wrappedIoStream = false
        )
        validateWholeRequest(
            url = url.toString(),
            startTime = REQUEST_TIME,
            endTime = REQUEST_TIME,
            httpMethod = HttpMethod.POST.name,
            httpStatus = HTTP_OK,
            responseBodySize = responseBodySize,
            requestSize = 0
        )
    }

    @Test
    fun `incomplete network request with uncompressed responses from a wrapped output stream are recorded properly`() {
        executeRequest(
            connection = createMockUncompressedConnection(),
            wrappedIoStream = true,
            exceptionOnInputStream = true
        )
        validateWholeRequest(
            url = url.toString(),
            startTime = REQUEST_TIME,
            endTime = REQUEST_TIME,
            httpMethod = HttpMethod.POST.name,
            httpStatus = null,
            responseBodySize = 0,
            requestSize = 0,
            errorType = IO_ERROR,
            errorMessage = "nope"
        )
    }

    @Test
    fun `incomplete network request with compressed responses from a wrapped output stream are recorded properly`() {
        executeRequest(
            connection = createMockGzipConnection(),
            wrappedIoStream = true,
            exceptionOnInputStream = true
        )
        validateWholeRequest(
            url = url.toString(),
            startTime = REQUEST_TIME,
            endTime = REQUEST_TIME,
            httpMethod = HttpMethod.POST.name,
            httpStatus = null,
            responseBodySize = 0,
            requestSize = 0,
            errorType = IO_ERROR,
            errorMessage = "nope"
        )
    }

    @Test
    fun `incomplete network request with uncompressed responses from an unwrapped output stream are recorded properly`() {
        executeRequest(
            connection = createMockUncompressedConnection(),
            wrappedIoStream = false,
            exceptionOnInputStream = true
        )
        validateWholeRequest(
            url = url.toString(),
            startTime = REQUEST_TIME,
            endTime = REQUEST_TIME,
            httpMethod = HttpMethod.POST.name,
            httpStatus = null,
            responseBodySize = 0,
            requestSize = 0,
            errorType = IO_ERROR,
            errorMessage = "nope"
        )
    }

    @Test
    fun `incomplete network request with compressed responses from an unwrapped output stream are recorded properly`() {
        executeRequest(
            connection = createMockGzipConnection(),
            wrappedIoStream = false,
            exceptionOnInputStream = true
        )
        validateWholeRequest(
            url = url.toString(),
            startTime = REQUEST_TIME,
            endTime = REQUEST_TIME,
            httpMethod = HttpMethod.POST.name,
            httpStatus = null,
            responseBodySize = 0,
            requestSize = 0,
            errorType = IO_ERROR,
            errorMessage = "nope"
        )
    }

    @Test
    fun `completed unsuccessful requests are recorded properly`() {
        executeRequest(
            connection = createMockGzipConnection(expectedResponseCode = 500),
            wrappedIoStream = true
        )
        validateWholeRequest(
            url = url.toString(),
            startTime = REQUEST_TIME,
            endTime = REQUEST_TIME,
            httpMethod = HttpMethod.POST.name,
            httpStatus = 500,
            responseBodySize = gzippedResponseBodySize,
            requestSize = requestBodySize,
            networkDataCaptured = true,
            responseBody = responseBodyText
        )
    }

    @Test
    fun `completed requests with custom paths are recorded properly`() {
        executeRequest(
            connection = createMockConnectionWithPathOverride(),
            wrappedIoStream = true
        )
        validateWholeRequest(
            url = customUrl.toString(),
            startTime = REQUEST_TIME,
            endTime = REQUEST_TIME,
            httpMethod = HttpMethod.POST.name,
            httpStatus = HTTP_OK,
            responseBodySize = gzippedResponseBodySize,
            requestSize = requestBodySize,
            networkDataCaptured = true,
            responseBody = responseBodyText
        )
    }

    @Test
    fun `incomplete requests with custom paths are recorded properly`() {
        executeRequest(
            connection = createMockConnectionWithPathOverride(),
            wrappedIoStream = true,
            exceptionOnInputStream = true
        )
        validateWholeRequest(
            url = customUrl.toString(),
            startTime = REQUEST_TIME,
            endTime = REQUEST_TIME,
            httpMethod = HttpMethod.POST.name,
            httpStatus = null,
            responseBodySize = 0,
            requestSize = 0,
            errorType = IO_ERROR,
            errorMessage = "nope"
        )
    }

    @Test
    fun `completed requests are not recorded if the SDK has not started`() {
        isSDKStarted = false
        executeRequest(
            connection = createMockGzipConnection(),
            wrappedIoStream = true
        )
        verify(exactly = 0) { mockInternalInterface.recordNetworkRequest(any()) }
    }

    @Test
    fun `incomplete requests are not recorded if the SDK has not started`() {
        isSDKStarted = false
        executeRequest(
            connection = createMockGzipConnection(),
            wrappedIoStream = true,
            exceptionOnInputStream = true
        )
        verify(exactly = 0) { mockInternalInterface.recordNetworkRequest(any()) }
    }

    @Test
    fun `completed network call logged twice with same callId with a wrapped output stream`() {
        executeRequest(
            connection = createMockUncompressedConnection(),
            wrappedIoStream = true
        )
        verifyTwoCallsRecordedWithSameCallId()
    }

    @Test
    fun `completed network call logged exactly once with unwrapped output stream`() {
        executeRequest(
            connection = createMockUncompressedConnection(),
            wrappedIoStream = false
        )
        verify(exactly = 1) { mockInternalInterface.recordNetworkRequest(any()) }
        assertTrue(capturedCallId[0].isNotBlank())
    }

    @Test
    fun `incomplete network call logged exactly once wrapped output stream`() {
        executeRequest(
            connection = createMockUncompressedConnection(),
            wrappedIoStream = true,
            exceptionOnInputStream = true
        )
        verify(exactly = 1) { mockInternalInterface.recordNetworkRequest(any()) }
    }

    @Test
    fun `disconnect called with previously not connected connection results in error request capture and no response access`() {
        val mockConnection = createMockUncompressedConnection()
        EmbraceUrlConnectionDelegate(mockConnection, true, mockEmbrace).disconnect()
        verifyIncompleteRequestLogged(mockConnection)
        verify(exactly = 1) { mockInternalInterface.recordNetworkRequest(any()) }
        assertEquals(1, capturedCallId.size)
    }

    @Test
    fun `incomplete network request logged when there's a failure in accessing the response content length`() {
        val mockConnection = createMockUncompressedConnection()
        every { mockConnection.contentLength } answers { throw TimeoutException() }

        executeRequest(connection = mockConnection, wrappedIoStream = true)
        verifyIncompleteRequestLogged(mockConnection = mockConnection, errorType = TIMEOUT_ERROR, noResponseAccess = false)
        verifyTwoCallsRecordedWithSameCallId()
    }

    @Test
    fun `incomplete network request logged when there's a failure in accessing the response code`() {
        val mockConnection = createMockUncompressedConnection()
        every { mockConnection.responseCode } answers { throw TimeoutException() }

        executeRequest(connection = mockConnection, wrappedIoStream = true)
        verifyIncompleteRequestLogged(mockConnection = mockConnection, errorType = TIMEOUT_ERROR, noResponseAccess = false)
        verifyTwoCallsRecordedWithSameCallId()
    }

    @Test
    fun `incomplete network request logged when there's a failure in accessing the response headers`() {
        val mockConnection = createMockUncompressedConnection()
        every { mockConnection.headerFields } answers { throw TimeoutException() }

        executeRequest(connection = mockConnection, wrappedIoStream = true)
        verifyIncompleteRequestLogged(mockConnection = mockConnection, errorType = TIMEOUT_ERROR, noResponseAccess = false)
        verifyTwoCallsRecordedWithSameCallId()
    }

    @Test
    fun `complete network request logged when network data capture is off even if reading request body throws exception`() {
        val mockConnection = createMockUncompressedConnection()
        every { (mockConnection.outputStream as CountingOutputStream).requestBody } answers { throw NullPointerException() }

        executeRequest(connection = mockConnection, wrappedIoStream = true)
        with(capturedEmbraceNetworkRequest.captured) {
            assertEquals(HTTP_OK, responseCode)
            assertNull(errorType)
        }
    }

    @Test
    fun `check traceparents are not forwarded by default`() {
        executeRequest(
            connection = createMockConnectionWithTraceparent(),
            wrappedIoStream = true
        )
        assertNull(capturedEmbraceNetworkRequest.captured.w3cTraceparent)
        assertEquals(HTTP_OK, capturedEmbraceNetworkRequest.captured.responseCode)
    }

    @Test
    fun `check traceparents are not forwarded on errors by default`() {
        executeRequest(
            connection = createMockConnectionWithTraceparent(),
            wrappedIoStream = true,
            exceptionOnInputStream = true
        )
        assertNull(capturedEmbraceNetworkRequest.captured.responseCode)
        assertEquals(IO_ERROR, capturedEmbraceNetworkRequest.captured.errorType)
        assertNull(capturedEmbraceNetworkRequest.captured.w3cTraceparent)
    }

    @Test
    fun `check traceparents are forwarded if feature flag is on`() {
        isNetworkSpanForwardingEnabled = true
        executeRequest(
            connection = createMockConnectionWithTraceparent(),
            wrappedIoStream = true
        )
        assertEquals(HTTP_OK, capturedEmbraceNetworkRequest.captured.responseCode)
        assertEquals(TRACEPARENT, capturedEmbraceNetworkRequest.captured.w3cTraceparent)
    }

    @Test
    fun `check traceparents are forwarded on errors if feature flag is on`() {
        isNetworkSpanForwardingEnabled = true
        executeRequest(
            connection = createMockConnectionWithTraceparent(),
            wrappedIoStream = true,
            exceptionOnInputStream = true
        )
        assertNull(capturedEmbraceNetworkRequest.captured.responseCode)
        assertEquals(TRACEPARENT, capturedEmbraceNetworkRequest.captured.w3cTraceparent)
        assertEquals(IO_ERROR, capturedEmbraceNetworkRequest.captured.errorType)
    }

    @Test
    fun `check traceIds are logged if a custom header name is specified`() {
        traceIdHeaderName = "my-trace-id-header"
        executeRequest(
            connection = createMockGzipConnection(
                extraRequestHeaders = mapOf(Pair("my-trace-id-header", listOf(customTraceId)))
            ),
            wrappedIoStream = true
        )
        assertEquals(HTTP_OK, capturedEmbraceNetworkRequest.captured.responseCode)
        assertEquals(customTraceId, capturedEmbraceNetworkRequest.captured.traceId)
    }

    private fun createMockConnectionWithPathOverride() = createMockGzipConnection(
        extraRequestHeaders = mapOf(Pair(PATH_OVERRIDE, listOf(customPath)))
    )

    private fun createMockConnectionWithTraceparent() = createMockGzipConnection(
        extraRequestHeaders = mapOf(Pair(TRACEPARENT_HEADER_NAME, listOf(TRACEPARENT)))
    )

    private fun createMockUncompressedConnection(): HttpsURLConnection {
        return createMockConnection(
            inputStream = ByteArrayInputStream(responseBodyBytes),
            expectedResponseSize = responseBodySize,
            expectedResponseCode = HTTP_OK
        )
    }

    private fun createMockGzipConnection(
        expectedResponseCode: Int = HTTP_OK,
        extraRequestHeaders: Map<String, List<String>> = emptyMap()
    ): HttpsURLConnection {
        return createMockConnection(
            inputStream = ByteArrayInputStream(gzippedResponseBodyBytes),
            extraRequestHeaders = extraRequestHeaders,
            expectedResponseSize = gzippedResponseBodySize,
            expectedResponseCode = expectedResponseCode,
            extraResponseHeaders = mapOf(
                Pair(CONTENT_ENCODING, listOf("gzip"))
            )
        )
    }

    private fun createMockConnection(
        inputStream: InputStream,
        extraRequestHeaders: Map<String, List<String>> = emptyMap(),
        expectedResponseSize: Int,
        expectedResponseCode: Int,
        extraResponseHeaders: Map<String, List<String>> = emptyMap()
    ): HttpsURLConnection {
        val mockConnection: HttpsURLConnection = mockk(relaxed = true)
        every { mockConnection.inputStream } answers { inputStream }
        every { mockConnection.contentLength } answers { expectedResponseSize }

        val outputStream = ByteArrayOutputStream(requestBodySize)
        outputStream.write(requestBodyBytes)
        every { mockConnection.outputStream } answers { outputStream }

        val requestHeaders = mutableMapOf(
            Pair(requestHeaderName, listOf(requestHeaderValue)),
            Pair(CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE, listOf(defaultTraceId))
        )

        if (extraRequestHeaders.isNotEmpty()) {
            requestHeaders += extraRequestHeaders
        }

        val responseHeaders = mutableMapOf(
            Pair(CONTENT_LENGTH, listOf(expectedResponseSize.toString())),
            Pair(responseHeaderName, listOf(responseHeaderValue))
        )

        if (extraResponseHeaders.isNotEmpty()) {
            responseHeaders += extraResponseHeaders
        }

        every { mockConnection.requestProperties } answers { requestHeaders }
        every { mockConnection.headerFields } answers { responseHeaders }
        every { mockConnection.url } answers { url }
        every { mockConnection.getRequestProperty(TRACEPARENT_HEADER_NAME) } answers {
            requestHeaders[TRACEPARENT_HEADER_NAME]?.get(0)
        }
        every { mockConnection.getRequestProperty(traceIdHeaderName) } answers {
            requestHeaders[traceIdHeaderName]?.get(0)
        }
        every { mockConnection.getRequestProperty(PATH_OVERRIDE) } answers {
            requestHeaders[PATH_OVERRIDE]?.get(0)
        }
        every { mockConnection.getRequestProperty(CONTENT_ENCODING) } answers {
            requestHeaders[CONTENT_ENCODING]?.get(0)
        }
        every { mockConnection.contentEncoding } answers { responseHeaders[CONTENT_ENCODING]?.get(0) }
        every { mockConnection.requestMethod } answers { HttpMethod.POST.name }
        every { mockConnection.responseCode } answers { expectedResponseCode }

        return mockConnection
    }

    private fun executeRequest(
        connection: HttpsURLConnection,
        wrappedIoStream: Boolean = false,
        exceptionOnInputStream: Boolean = false
    ) {
        val delegate = EmbraceUrlConnectionDelegate<HttpsURLConnection>(connection, wrappedIoStream, mockEmbrace)
        with(delegate) {
            connect()
            setRequestProperty(requestHeaderName, requestHeaderValue)
            outputStream?.write(requestBodyBytes)
            if (exceptionOnInputStream) {
                every { connection.inputStream } answers { throw IOException("nope") }
                assertThrows(IOException::class.java) { inputStream }
            } else {
                val input = inputStream
                headerFields
                responseCode
                val b = ByteArray(8192)
                input?.read(b)
                assertEquals(-1, input?.read())
            }
            disconnect()
        }
    }

    @Suppress("LongParameterList")
    private fun validateWholeRequest(
        url: String,
        httpMethod: String,
        startTime: Long,
        endTime: Long,
        httpStatus: Int?,
        requestSize: Int?,
        responseBodySize: Int?,
        errorType: String? = null,
        errorMessage: String? = null,
        traceId: String = defaultTraceId,
        w3cTraceparent: String? = null,
        networkDataCaptured: Boolean = false,
        responseBody: String? = null
    ) {
        with(capturedEmbraceNetworkRequest) {
            assertEquals(url, captured.url)
            assertEquals(httpMethod, captured.httpMethod)
            assertEquals(startTime, captured.startTime)
            assertEquals(endTime, captured.endTime)
            assertEquals(httpStatus, captured.responseCode)
            assertEquals(requestSize?.toLong(), captured.bytesOut)
            assertEquals(responseBodySize?.toLong(), captured.bytesIn)
            assertEquals(errorType, captured.errorType)
            assertEquals(errorMessage, captured.errorMessage)
            assertEquals(traceId, captured.traceId)
            assertEquals(w3cTraceparent, captured.w3cTraceparent)
            if (networkDataCaptured) {
                validateNetworkCaptureData(responseBody)
            } else {
                assertNull(captured.networkCaptureData)
            }
        }
    }

    private fun validateNetworkCaptureData(responseBody: String?) {
        with(checkNotNull(capturedEmbraceNetworkRequest.captured.networkCaptureData)) {
            assertEquals(requestHeaderValue, checkNotNull(requestHeaders)[requestHeaderName])
            assertEquals(responseHeaderValue, checkNotNull(responseHeaders)[responseHeaderName])
            assertEquals(defaultQueryString, requestQueryParams)
            assertEquals(requestBodyText, capturedRequestBody?.toString(Charsets.UTF_8))
            if (responseBody == null) {
                assertNull(capturedRequestBody)
            } else {
                assertEquals(responseBody, capturedResponseBody?.toString(Charsets.UTF_8))
            }

            assertNull(dataCaptureErrorMessage)
        }
    }

    private fun verifyIncompleteRequestLogged(
        mockConnection: HttpsURLConnection,
        errorType: String = "UnknownState",
        noResponseAccess: Boolean = true
    ) {
        if (noResponseAccess) {
            verify(exactly = 0) { mockConnection.responseCode }
            verify(exactly = 0) { mockConnection.contentLength }
            verify(exactly = 0) { mockConnection.headerFields }
        }
        assertNull(capturedEmbraceNetworkRequest.captured.responseCode)
        assertEquals(errorType, capturedEmbraceNetworkRequest.captured.errorType)
    }

    private fun verifyTwoCallsRecordedWithSameCallId() {
        verify(exactly = 2) { mockInternalInterface.recordNetworkRequest(any()) }
        assertEquals(2, capturedCallId.size)
        assertEquals(capturedCallId[0], capturedCallId[1])
    }

    companion object {
        private fun String.toGzipByteArray(): ByteArray {
            val outputStream = ByteArrayOutputStream()
            GZIPOutputStream(outputStream).bufferedWriter(Charsets.UTF_8).use { it.write(this) }
            return outputStream.toByteArray()
        }

        private const val TRACEPARENT = "00-3c72a77a7b51af6fb3778c06d4c165ce-4c1d710fffc88e35-01"
        private const val HTTP_OK = 200
        private const val REQUEST_TIME = 1692201601000L
        private const val requestBodyText = "test"
        private const val requestHeaderName = "requestHeader"
        private const val requestHeaderValue = "requestHeaderVal"
        private const val defaultQueryString = "param=yesPlease"
        private const val defaultPath = "/test/default-path"
        private const val customPath = "/test/custom-path"
        private const val defaultHost = "embrace.io"
        private const val responseBodyText = "derpderpderpderp"
        private const val responseHeaderName = "responseHeader"
        private const val responseHeaderValue = "responseHeaderVal"
        private const val defaultTraceId = "default-trace-id"
        private const val customTraceId = "custom-trace-id"
        private val url = URL("https", defaultHost, 1881, "$defaultPath?$defaultQueryString")
        private val customUrl = URL("https", defaultHost, 1881, "$customPath?$defaultQueryString")
        private val requestBodyBytes = requestBodyText.toByteArray()
        private val requestBodySize = requestBodyBytes.size
        private val responseBodyBytes = responseBodyText.toByteArray()
        private val responseBodySize = responseBodyBytes.size
        private val gzippedResponseBodyBytes = responseBodyText.toGzipByteArray()
        private val gzippedResponseBodySize = gzippedResponseBodyBytes.size
        private val IO_ERROR = checkNotNull(IOException::class.java.canonicalName)
        private val TIMEOUT_ERROR = checkNotNull(TimeoutException::class.java.canonicalName)
    }
}
