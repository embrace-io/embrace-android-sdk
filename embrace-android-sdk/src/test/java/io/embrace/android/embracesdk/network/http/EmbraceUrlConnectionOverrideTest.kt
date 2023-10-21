package io.embrace.android.embracesdk.network.http

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.config.behavior.NetworkSpanForwardingBehavior.Companion.TRACEPARENT_HEADER_NAME
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeoutException
import javax.net.ssl.HttpsURLConnection

internal class EmbraceUrlConnectionOverrideTest {

    private lateinit var mockEmbrace: Embrace
    private lateinit var mockInternalInterface: EmbraceInternalInterface
    private lateinit var mockConnection: HttpsURLConnection
    private lateinit var capturedCallId: MutableList<String>
    private lateinit var capturedEmbraceNetworkRequest: CapturingSlot<EmbraceNetworkRequest>
    private lateinit var embraceUrlConnectionOverride: EmbraceUrlConnectionOverride<HttpsURLConnection>
    private lateinit var embraceUrlConnectionOverrideUnwrapped: EmbraceUrlConnectionOverride<HttpsURLConnection>
    private var shouldCaptureNetworkBody = false
    private var isNetworkSpanForwardingEnabled = false

    @Before
    fun setup() {
        mockEmbrace = mockk(relaxed = true)
        every { mockEmbrace.internalInterface } answers { mockInternalInterface }
        shouldCaptureNetworkBody = false
        isNetworkSpanForwardingEnabled = false
        capturedCallId = mutableListOf()
        capturedEmbraceNetworkRequest = slot()
        mockInternalInterface = mockk(relaxed = true)
        every { mockInternalInterface.shouldCaptureNetworkBody(any(), any()) } answers { shouldCaptureNetworkBody }
        every {
            mockInternalInterface.recordAndDeduplicateNetworkRequest(capture(capturedCallId), capture(capturedEmbraceNetworkRequest))
        } answers { }
        every { mockInternalInterface.isNetworkSpanForwardingEnabled() } answers { isNetworkSpanForwardingEnabled }
        mockConnection = createMockConnection()
        embraceUrlConnectionOverride = EmbraceUrlConnectionOverride(mockConnection, true, mockEmbrace)
        embraceUrlConnectionOverrideUnwrapped = EmbraceUrlConnectionOverride(mockConnection, false, mockEmbrace)
    }

    @Test
    fun `completed network call logged exactly once if connection connected with wrapped output stream`() {
        executeRequest()
        verifyTwoCallsRecordedWithSameCallId()
        with(capturedEmbraceNetworkRequest.captured) {
            assertEquals(HttpMethod.POST.name, httpMethod)
            assertEquals(HTTP_OK, responseCode)
            assertEquals(requestBodySize.toLong(), bytesSent)
            assertEquals(responseBodySize.toLong(), bytesReceived)
            assertNull(errorType)
        }
    }

    @Test
    fun `completed network call logged twice once if connection connected with wrapped output stream and network body captured`() {
        shouldCaptureNetworkBody = true
        executeRequest()
        verifyTwoCallsRecordedWithSameCallId()
        with(capturedEmbraceNetworkRequest.captured) {
            assertEquals(HttpMethod.POST.name, httpMethod)
            assertEquals(HTTP_OK, responseCode)
            assertEquals(requestBodySize.toLong(), bytesSent)
            assertEquals(responseBodySize.toLong(), bytesReceived)
            assertNotNull(networkCaptureData)
            assertNull(errorType)
        }
    }

    @Test
    fun `completed network call logged exactly once with no request size if connection connected with unwrapped output stream`() {
        executeRequest(embraceOverride = embraceUrlConnectionOverrideUnwrapped)
        verify(exactly = 1) { mockInternalInterface.recordAndDeduplicateNetworkRequest(any(), any()) }
        assertTrue(capturedCallId[0].isNotBlank())
        with(capturedEmbraceNetworkRequest.captured) {
            assertEquals(HttpMethod.POST.name, httpMethod)
            assertEquals(HTTP_OK, responseCode)
            assertEquals(0L, bytesSent)
            assertEquals(responseBodySize.toLong(), bytesReceived)
            assertNull(errorType)
        }
    }

    @Test
    fun `incomplete network call logged exactly once and response data not accessed if connection connected`() {
        executeRequest(exceptionOnInputStream = true)
        verify(exactly = 1) { mockInternalInterface.recordAndDeduplicateNetworkRequest(any(), any()) }
        assertTrue(capturedCallId[0].isNotBlank())
        verify(exactly = 0) { mockConnection.responseCode }
        verify(exactly = 0) { mockConnection.contentLength }
        verify(exactly = 0) { mockConnection.headerFields }
        with(capturedEmbraceNetworkRequest.captured) {
            assertEquals(HttpMethod.POST.name, httpMethod)
            assertNull(responseCode)
            assertEquals(null, bytesSent)
            assertEquals(null, bytesReceived)
            assertEquals(IO_ERROR, errorType)
        }
    }

    @Test
    fun `disconnect called with uninitialized connection results in error request capture and no response access`() {
        embraceUrlConnectionOverride.disconnect()
        verifyIncompleteRequestLogged()
        verify(exactly = 1) { mockInternalInterface.recordAndDeduplicateNetworkRequest(any(), any()) }
        assertEquals(1, capturedCallId.size)
    }

    @Test
    fun `incomplete network request logged when there's a failure in accessing the response content length`() {
        every { mockConnection.contentLength } answers { throw TimeoutException() }
        executeRequest()
        verifyIncompleteRequestLogged(errorType = TIMEOUT_ERROR, noResponseAccess = false)
        verifyTwoCallsRecordedWithSameCallId()
    }

    @Test
    fun `incomplete network request logged when there's a failure in accessing the response code`() {
        every { mockConnection.responseCode } answers { throw TimeoutException() }
        executeRequest()
        verifyIncompleteRequestLogged(errorType = TIMEOUT_ERROR, noResponseAccess = false)
        verifyTwoCallsRecordedWithSameCallId()
    }

    @Test
    fun `incomplete network request logged when there's a failure in accessing the response headers`() {
        every { mockConnection.headerFields } answers { throw TimeoutException() }
        executeRequest()
        verifyIncompleteRequestLogged(errorType = TIMEOUT_ERROR, noResponseAccess = false)
        verifyTwoCallsRecordedWithSameCallId()
    }

    @Test
    fun `complete network request logged when network data capture is off even if reading request body throws exception`() {
        every { (mockConnection.outputStream as CountingOutputStream).requestBody } answers { throw NullPointerException() }
        executeRequest()
        with(capturedEmbraceNetworkRequest.captured) {
            assertEquals(HTTP_OK, responseCode)
            assertNull(errorType)
        }
    }

    @Test
    fun `check traceheaders are not forwarded by default`() {
        executeRequest()
        assertNull(capturedEmbraceNetworkRequest.captured.w3cTraceparent)
        assertEquals(HTTP_OK, capturedEmbraceNetworkRequest.captured.responseCode)
    }

    @Test
    fun `check traceheaders are not forwarded on errors by default`() {
        executeRequest(exceptionOnInputStream = true)
        assertNull(capturedEmbraceNetworkRequest.captured.responseCode)
        assertEquals(IO_ERROR, capturedEmbraceNetworkRequest.captured.errorType)
        assertNull(capturedEmbraceNetworkRequest.captured.w3cTraceparent)
    }

    @Test
    fun `check traceheaders are forwarded if feature flag is on`() {
        isNetworkSpanForwardingEnabled = true
        executeRequest()
        assertEquals(HTTP_OK, capturedEmbraceNetworkRequest.captured.responseCode)
        assertEquals(TRACEPARENT, capturedEmbraceNetworkRequest.captured.w3cTraceparent)
    }

    @Test
    fun `check traceheaders are forwarded on errors if feature flag is on`() {
        isNetworkSpanForwardingEnabled = true
        executeRequest(exceptionOnInputStream = true)
        assertNull(capturedEmbraceNetworkRequest.captured.responseCode)
        assertEquals(TRACEPARENT, capturedEmbraceNetworkRequest.captured.w3cTraceparent)
        assertEquals(IO_ERROR, capturedEmbraceNetworkRequest.captured.errorType)
    }

    private fun createMockConnection(): HttpsURLConnection {
        val connection: HttpsURLConnection = mockk(relaxed = true)
        val mockOutputStream: CountingOutputStream = mockk(relaxed = true)
        val inputStream: InputStream = ByteArrayInputStream(responseBody)
        every { mockOutputStream.requestBody } answers { requestBody }
        every { connection.outputStream } answers { mockOutputStream }
        every { connection.getRequestProperty(TRACEPARENT_HEADER_NAME) } answers { TRACEPARENT }
        every { connection.requestMethod } answers { HttpMethod.POST.name }
        every { connection.responseCode } answers { HTTP_OK }
        every { connection.contentLength } answers { responseBodySize }
        every { connection.headerFields } answers {
            mapOf(
                Pair("Content-Encoding", listOf("gzip")),
                Pair("Content-Length", listOf(responseBodySize.toString())),
                Pair("myHeader", listOf("myValue"))
            )
        }
        every { connection.inputStream } answers { inputStream }
        return connection
    }

    private fun executeRequest(
        embraceOverride: EmbraceUrlConnectionOverride<HttpsURLConnection> = embraceUrlConnectionOverride,
        exceptionOnInputStream: Boolean = false
    ) {
        with(embraceOverride) {
            connect()
            outputStream?.write(requestBody)
            if (exceptionOnInputStream) {
                every { mockConnection.inputStream } answers { throw IOException() }
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

    private fun verifyIncompleteRequestLogged(errorType: String = "UnknownState", noResponseAccess: Boolean = true) {
        if (noResponseAccess) {
            verify(exactly = 0) { mockConnection.responseCode }
            verify(exactly = 0) { mockConnection.contentLength }
            verify(exactly = 0) { mockConnection.headerFields }
        }
        assertNull(capturedEmbraceNetworkRequest.captured.responseCode)
        assertEquals(errorType, capturedEmbraceNetworkRequest.captured.errorType)
    }

    private fun verifyTwoCallsRecordedWithSameCallId() {
        verify(exactly = 2) { mockInternalInterface.recordAndDeduplicateNetworkRequest(any(), any()) }
        assertEquals(2, capturedCallId.size)
        assertEquals(capturedCallId[0], capturedCallId[1])
    }

    companion object {
        private const val TRACEPARENT = "00-3c72a77a7b51af6fb3778c06d4c165ce-4c1d710fffc88e35-01"
        private const val HTTP_OK = 200
        private val requestBody = "test".toByteArray()
        private val requestBodySize = requestBody.size
        private val responseBody = "responseresponse".toByteArray()
        private val responseBodySize = responseBody.size
        private val IO_ERROR = checkNotNull(IOException::class.java.canonicalName)
        private val TIMEOUT_ERROR = checkNotNull(TimeoutException::class.java.canonicalName)
    }
}
