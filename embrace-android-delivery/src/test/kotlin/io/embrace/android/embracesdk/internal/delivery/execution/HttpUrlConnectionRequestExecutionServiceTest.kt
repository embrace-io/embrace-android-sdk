package io.embrace.android.embracesdk.internal.delivery.execution

import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import okhttp3.Headers.Companion.toHeaders
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class HttpUrlConnectionRequestExecutionServiceTest {
    private lateinit var requestExecutionService: HttpUrlConnectionRequestExecutionService
    private lateinit var server: MockWebServer
    private lateinit var testServerUrl: String
    private lateinit var logger: FakeEmbLogger

    private val testAppId = "test_app_id"
    private val testDeviceId = "test_device_id"
    private val testEmbraceVersionName = "1.23.4-SNAPSHOT"
    private val testPostBody = """
        {
            "resource": {
              "app_version": "1.0"
            },
            "data": {
              "spans": [
                {
                  "trace_id": "1059afad3d8879dab8660f1da2ec1087",
                  "span_id": "1d983f73141bf821"
                }
              ]
            }
           }
    """

    @Before
    fun setUp() {
        logger = FakeEmbLogger()
        server = MockWebServer()
        server.start()
        testServerUrl = server.url("").toString().removeSuffix("/")
        requestExecutionService = HttpUrlConnectionRequestExecutionService(
            coreBaseUrl = testServerUrl,
            lazyDeviceId = lazy { testDeviceId },
            appId = testAppId,
            embraceVersionName = testEmbraceVersionName,
            logger = logger,
            connectionTimeoutMilliseconds = 2000,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `return incomplete if the server does not exist`() {
        // given a request execution service with a non existent url
        requestExecutionService = HttpUrlConnectionRequestExecutionService(
            coreBaseUrl = "http://nonexistenturl:1565",
            lazyDeviceId = lazy { testDeviceId },
            appId = testAppId,
            embraceVersionName = testEmbraceVersionName,
            logger = logger,
        )

        // when attempting to make a request
        val response = requestExecutionService.attemptHttpRequest(
            payloadStream = { testPostBody.byteInputStream() },
            envelopeType = SupportedEnvelopeType.SESSION,
            payloadType = PayloadType.SESSION.value
        )

        // then the response should be incomplete
        check(response is ExecutionResult.Incomplete)
        assertTrue(response.exception is UnknownHostException)
    }

    @Test
    fun `return success if the server returns a 200 response`() {
        // given a server that returns a 200 response
        server.enqueue(MockResponse().setResponseCode(200))

        // when attempting to make a request
        val response = requestExecutionService.attemptHttpRequest(
            payloadStream = { testPostBody.byteInputStream() },
            envelopeType = SupportedEnvelopeType.SESSION,
            payloadType = PayloadType.SESSION.value
        )

        // then the response should be successful
        assertTrue(response is ExecutionResult.Success)
    }

    @Test
    fun `return other result if 304 response received`() {
        // given a server that returns a 304 response
        server.enqueue(MockResponse().setResponseCode(304))

        // when attempting to make a request
        val response = requestExecutionService.attemptHttpRequest(
            payloadStream = { testPostBody.byteInputStream() },
            envelopeType = SupportedEnvelopeType.SESSION,
            payloadType = PayloadType.SESSION.value
        )

        // then the response should be not modified
        assertTrue(response is ExecutionResult.Other)
    }

    @Test
    fun `return payload too large if the server returns a 413 response`() {
        // given a server that returns a 413 response
        server.enqueue(MockResponse().setResponseCode(413))

        // when attempting to make a request
        val response = requestExecutionService.attemptHttpRequest(
            payloadStream = { testPostBody.byteInputStream() },
            envelopeType = SupportedEnvelopeType.SESSION,
            payloadType = PayloadType.SESSION.value
        )

        // then the response should be payload too large
        assertTrue(response is ExecutionResult.PayloadTooLarge)
    }

    @Test
    fun `return too many requests if the server returns a 429 response`() {
        // given a server that returns a 429 response
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeaders(mapOf("Retry-After" to "10").toHeaders())
        )

        // when attempting to make a request
        val response = requestExecutionService.attemptHttpRequest(
            payloadStream = { testPostBody.byteInputStream() },
            envelopeType = SupportedEnvelopeType.SESSION,
            payloadType = PayloadType.SESSION.value
        )

        // then the response should be too many requests
        check(response is ExecutionResult.TooManyRequests)
        assertEquals(10L, response.retryAfter)
    }

    @Test
    fun `return incomplete with no internal error logged if the execution throws IOException`() {
        // given a server that disconnects before sending a response (thus causing a timeout)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        // when attempting to make a request
        val response = requestExecutionService.attemptHttpRequest(
            payloadStream = { testPostBody.byteInputStream() },
            envelopeType = SupportedEnvelopeType.SESSION,
            payloadType = PayloadType.SESSION.value
        )

        // then the response should be incomplete
        check(response is ExecutionResult.Incomplete)
        assertTrue(response.retry)
        assertTrue(response.exception is SocketTimeoutException)
        assertEquals(0, logger.internalErrorMessages.size)
    }

    @Test
    fun `return failure if the server returns any other response`() {
        // given a server that returns a 500 response
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeaders(mapOf("Custom-Error-Header" to "ouch").toHeaders())
        )

        // when attempting to make a request
        val response = requestExecutionService.attemptHttpRequest(
            payloadStream = { testPostBody.byteInputStream() },
            envelopeType = SupportedEnvelopeType.SESSION,
            payloadType = PayloadType.SESSION.value
        )

        // then the response should be failure
        check(response is ExecutionResult.Failure)
        assertEquals(500, response.code)
    }

    @Test
    fun `headers are sent correctly`() {
        // given a server that returns a 200 response
        server.enqueue(MockResponse().setResponseCode(200))

        // when attempting to make a request
        requestExecutionService.attemptHttpRequest(
            payloadStream = { testPostBody.byteInputStream() },
            envelopeType = SupportedEnvelopeType.SESSION,
            payloadType = PayloadType.SESSION.value
        )

        // then the request should include the expected headers
        val request = server.takeRequest()

        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("Embrace/a/$testEmbraceVersionName", request.getHeader("User-Agent"))
        assertEquals("application/json", request.getHeader("Content-Type"))
        assertEquals("gzip", request.getHeader("Content-Encoding"))
        assertEquals(testAppId, request.getHeader("X-EM-AID"))
        assertEquals(testDeviceId, request.getHeader("X-EM-DID"))
        assertEquals(PayloadType.SESSION.value, request.getHeader("X-EM-TYPES"))
    }

    @Test
    fun `payload type header is sent correctly`() {
        // given a server that returns a 200 response
        server.enqueue(MockResponse().setResponseCode(200))

        // when attempting to make a request
        requestExecutionService.attemptHttpRequest(
            payloadStream = { testPostBody.byteInputStream() },
            envelopeType = SupportedEnvelopeType.LOG,
            payloadType = PayloadType.AEI.value
        )

        // then the request should include the expected headers
        val request = server.takeRequest()

        assertEquals(PayloadType.AEI.value, request.getHeader("X-EM-TYPES"))
    }
}
