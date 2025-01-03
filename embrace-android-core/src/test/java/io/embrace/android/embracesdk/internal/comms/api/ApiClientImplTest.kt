package io.embrace.android.embracesdk.internal.comms.api

import io.embrace.android.embracesdk.internal.compression.ConditionalGzipOutputStream
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.network.http.HttpMethod
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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
        apiClient = ApiClientImpl()
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
        val request = ApiRequest(url = ApiRequestUrl("http://localhost:1565"), userAgent = "test")
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
        check(response is ApiResponse.Success)
        assertEquals(DEFAULT_RESPONSE_BODY, response.body)
    }

    @Test
    fun testGet200CompressedResponse() {
        server.enqueue(compressedResponse200)
        val response = runGetRequest(encoding = "gzip")
        check(response is ApiResponse.Success)
        assertEquals(DEFAULT_RESPONSE_BODY, response.body)
    }

    @Test
    fun testPost200ResponseCompressed() {
        server.enqueue(compressedResponse200)
        val response = runPostRequest(encoding = "gzip")
        check(response is ApiResponse.Success)
        assertEquals(DEFAULT_RESPONSE_BODY, response.body)

        val delivered = tryTakeRequest()
        assertEquals(HttpMethod.POST.name, delivered.method)
        assertEquals("/test", delivered.path)
        assertTrue(delivered.headers.contains("Content-Length" to "${delivered.bodySize}"))
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
        val delivered = tryTakeRequest()
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
            contentType = "application/json",
            userAgent = "Embrace/a/1",
            contentEncoding = "gzip",
            accept = "application/json",
            acceptEncoding = "gzip",
            appId = "abcde",
            deviceId = "test_did",
            url = ApiRequestUrl(baseUrl)
        )
        server.enqueue(response200)
        apiClient.executePost(postRequest) {
            it.write(DEFAULT_REQUEST_BODY.toByteArray())
        }

        // assert all request headers were set
        val delivered = tryTakeRequest()
        val headers = delivered.headers.toMap()
            .minus("Host")
            .minus("Connection")
        assertEquals(
            mapOf(
                "Accept" to "application/json",
                "User-Agent" to "Embrace/a/1",
                "Content-Type" to "application/json",
                "Content-Encoding" to "gzip",
                "Accept-Encoding" to "gzip",
                "X-EM-AID" to "abcde",
                "X-EM-DID" to "test_did",
                "Content-Length" to "${delivered.bodySize}",
            ),
            headers
        )
    }

    private fun tryTakeRequest() = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    private fun runGetRequest(encoding: String? = null): ApiResponse =
        apiClient.executeGet(
            ApiRequest(
                url = ApiRequestUrl(baseUrl),
                httpMethod = HttpMethod.GET,
                userAgent = "test",
                acceptEncoding = encoding,
            )
        )

    private fun runPostRequest(
        payload: ByteArray = DEFAULT_REQUEST_BODY.toByteArray(),
        encoding: String? = null,
    ): ApiResponse =
        apiClient.executePost(
            ApiRequest(
                url = ApiRequestUrl(baseUrl),
                httpMethod = HttpMethod.POST,
                userAgent = "test",
                acceptEncoding = encoding,
            )
        ) {
            ConditionalGzipOutputStream(it).use { stream ->
                stream.write(payload)
            }
        }

    private fun createThrowingRequest(): ApiRequest {
        return ApiRequest(url = ApiRequestUrl("my bad req"), userAgent = "test")
    }

    private fun RecordedRequest.readCompressedRequestBody(): String {
        val inputStream = body.inputStream()
        return GZIPInputStream(inputStream).bufferedReader().readText()
    }

    private fun createLargeSessionPayload(): String {
        val msg = SessionPayload(
            spans = listOf(
                Span(
                    attributes = (1..5000).map {
                        Attribute("my_big_key_$it", "my_big_val_$it")
                    }
                )
            )
        )
        return serializer.toJson(msg)
    }

    companion object {
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
            ).addHeader("Content-Encoding", "gzip")

        private const val DEFAULT_RESPONSE_BODY = "{}"
        private const val DEFAULT_REQUEST_BODY = "{}"
        private val response200 = MockResponse().setBody(DEFAULT_RESPONSE_BODY).setResponseCode(200)
        private val compressedResponse200 = MockResponse().setGzipBody(DEFAULT_RESPONSE_BODY).setResponseCode(200)
        private val response400 = MockResponse().setResponseCode(400)
        private val response500 = MockResponse().setResponseCode(500)
    }
}
