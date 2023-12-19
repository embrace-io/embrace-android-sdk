package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.IllegalStateException

/**
 * Runs a [MockWebServer] and asserts against our network code to ensure that it
 * robustly handles various scenarios.
 */
internal class ApiClientImplTest {

    private val serializer = EmbraceSerializer()
    private lateinit var apiClient: ApiClientImpl
    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        apiClient = ApiClientImpl(InternalEmbraceLogger())
        server = MockWebServer()
        server.start()
        baseUrl = server.url("test").toString()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testUnreachableHost() {
        // attempt some unreachable port
        val request = ApiRequest(url = EmbraceUrl.create("http://localhost:1565"))
        val response = apiClient.executePost(request) {
            it.write("Hello world".toByteArray())
        }
        check(response is ApiResponse.Incomplete)
        assertTrue(response.exception is IllegalStateException)
    }

    @Test
    fun testGet200Response() {
        server.enqueue(response200)
        val response = runGetRequest()

        assertGetRequest(server.takeRequest())
        check(response is ApiResponse.Success)
        assertEquals(DEFAULT_RESPONSE_BODY, response.body)
    }

    @Test
    fun testPost200ResponseCompressed() {
        server.enqueue(response200)
        val response = runPostRequest()
        check(response is ApiResponse.Success)
        assertEquals(DEFAULT_RESPONSE_BODY, response.body)

        val delivered = server.takeRequest()
        assertPostRequest(delivered)
        assertEquals(DEFAULT_REQUEST_BODY, delivered.readCompressedRequestBody())
    }

    @Test
    fun testGet400Response() {
        server.enqueue(response400)
        val response = runGetRequest()
        check(response is ApiResponse.Failure)
        assertEquals(response.code, 400)
    }

    @Test
    fun testPost400Response() {
        server.enqueue(response400)
        val response = runPostRequest()
        check(response is ApiResponse.Failure)
        assertEquals(response.code, 400)
    }

    @Test
    fun testGet500Response() {
        server.enqueue(response500)
        val response = runGetRequest()
        check(response is ApiResponse.Failure)
        assertEquals(response.code, 500)
    }

    @Test
    fun testPost500Response() {
        server.enqueue(response500)
        val response = runPostRequest()
        check(response is ApiResponse.Failure)
        assertEquals(response.code, 500)
    }

    @Test
    fun testGetConnectionThrows() {
        val response = apiClient.executeGet(createThrowingRequest())
        check(response is ApiResponse.Incomplete)
        assertTrue(response.exception is java.lang.IllegalStateException)
    }

    @Test
    fun testPostConnectionThrows() {
        val response = apiClient.executePost(createThrowingRequest()) {
            it.write(DEFAULT_REQUEST_BODY.toByteArray())
        }
        check(response is ApiResponse.Incomplete)
        assertTrue(response.exception is java.lang.IllegalStateException)
    }

    /**
     * Sends a large session payload & verifies that the server receives the expected JSON. The
     * large payload helps assert that buffering & GZIP compression work as expected.
     */
    @Test
    fun testSendLargePayload() {
        server.enqueue(response200)

        val payload = createLargeSessionPayload()
        val result = runPostRequest(payload = payload.toByteArray())

        check(result is ApiResponse.Success)
        // assert on result parsed by ApiClient
        assertEquals(DEFAULT_RESPONSE_BODY, result.body)

        // assert on request received by mock server
        val delivered = server.takeRequest()
        val observed = delivered.readCompressedRequestBody()
        assertEquals(payload, observed)
    }

    /**
     * Simulates an I/O exception midway through a request.
     */
    @Test
    fun testIoExceptionMidRequest() {
        server.enqueue(MockResponse().throttleBody(1, 1000, TimeUnit.MILLISECONDS))

        // shutdown the server mid-request
        Executors.newSingleThreadScheduledExecutor().schedule({
            server.shutdown()
        }, 25, TimeUnit.MILLISECONDS)

        // fire off the api request
        val response = runPostRequest()
        check(response is ApiResponse.Incomplete)
        assertTrue(response.exception is IllegalStateException)
    }

    @Test
    fun testAllRequestHeadersSet() {
        val postRequest = ApiRequest(
            "application/json",
            "Embrace/a/1",
            "gzip",
            "application/json",
            "gzip",
            "abcde",
            "test_did",
            "test_eid",
            "test_lid",
            EmbraceUrl.create(baseUrl)
        )
        server.enqueue(response200)
        apiClient.executePost(postRequest) {
            it.write(DEFAULT_REQUEST_BODY.toByteArray())
        }

        // assert all request headers were set
        val delivered = server.takeRequest()
        val headers = delivered.headers.toMap()
            .minus("Host")
        assertEquals(
            mapOf(
                "Accept" to "application/json",
                "User-Agent" to "Embrace/a/1",
                "Content-Type" to "application/json",
                "Content-Encoding" to "gzip",
                "Accept-Encoding" to "gzip",
                "Connection" to "keep-alive",
                "Content-Length" to "${delivered.bodySize}",
                "X-EM-AID" to "abcde",
                "X-EM-DID" to "test_did",
                "X-EM-SID" to "test_eid",
                "X-EM-LID" to "test_lid"
            ),
            headers
        )
    }

    private fun runGetRequest(): ApiResponse =
        apiClient.executeGet(
            ApiRequest(
                url = EmbraceUrl.create(baseUrl),
                httpMethod = HttpMethod.GET
            )
        )

    private fun runPostRequest(
        payload: ByteArray = DEFAULT_REQUEST_BODY.toByteArray()
    ): ApiResponse =
        apiClient.executePost(
            ApiRequest(
                url = EmbraceUrl.create(baseUrl),
                httpMethod = HttpMethod.POST
            )
        ) {
            it.write(payload)
        }

    private fun createThrowingRequest(): ApiRequest {
        val mockEmbraceUrl: EmbraceUrl = mockk(relaxed = true)
        every { mockEmbraceUrl.openConnection() } answers { throw SocketException() }
        return ApiRequest(url = mockEmbraceUrl)
    }

    private fun RecordedRequest.readCompressedRequestBody(): String {
        val inputStream = body.inputStream()
        return GZIPInputStream(inputStream).bufferedReader().readText()
    }

    private fun assertPostRequest(delivered: RecordedRequest) {
        assertEquals(HttpMethod.POST.name, delivered.method)
        assertEquals("/test", delivered.path)
        val headers = delivered.headers.toMap()
            .minus("Host")
        assertEquals(
            mapOf(
                "Accept" to "application/json",
                "User-Agent" to "Embrace/a/${BuildConfig.VERSION_NAME}",
                "Content-Type" to "application/json",
                "Connection" to "keep-alive",
                "Content-Length" to "${delivered.bodySize}",
            ),
            headers
        )
    }

    private fun assertGetRequest(delivered: RecordedRequest) {
        assertEquals(HttpMethod.GET.name, delivered.method)
        assertEquals("/test", delivered.path)
        val headers = delivered.headers.toMap()
            .minus("Host")
        assertEquals(
            mapOf(
                "Accept" to "application/json",
                "User-Agent" to "Embrace/a/${BuildConfig.VERSION_NAME}",
                "Content-Type" to "application/json",
                "Connection" to "keep-alive"
            ),
            headers
        )
    }

    private fun createLargeSessionPayload(): String {
        val props = (1..5000).associate { "my_big_key_$it" to "my_big_val_$it" }
        val session = fakeSession().copy(properties = props)
        return serializer.toJson(session)
    }

    companion object {
        private const val DEFAULT_RESPONSE_BODY = "{}"
        private const val DEFAULT_REQUEST_BODY = "{}"
        private val response200 = MockResponse().setBody(DEFAULT_RESPONSE_BODY).setResponseCode(200)
        private val response400 = MockResponse().setResponseCode(400)
        private val response500 = MockResponse().setResponseCode(500)
    }
}
