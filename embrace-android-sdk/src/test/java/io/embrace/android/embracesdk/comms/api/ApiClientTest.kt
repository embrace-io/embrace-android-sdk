package io.embrace.android.embracesdk.comms.api

import com.google.gson.Gson
import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Runs a [MockWebServer] and asserts against our network code to ensure that it
 * robustly handles various scenarios.
 */
internal class ApiClientTest {

    private lateinit var request: ApiRequest
    private lateinit var apiClient: ApiClient
    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        apiClient = ApiClient(
            InternalEmbraceLogger()
        )

        // create mock web server
        server = MockWebServer()
        server.start()
        baseUrl = server.url("test").toString()
        request = ApiRequest(url = EmbraceUrl.getUrl(baseUrl))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test(expected = RuntimeException::class)
    fun testUnreachableHost() {
        // attempt some unreachable port
        val request = ApiRequest(url = EmbraceUrl.getUrl("http://localhost:1565"))
        apiClient.post(request, "Hello world".toByteArray())
    }

    @Test
    fun test200ResponseUncompressed() {
        server.enqueue(MockResponse().setBody(DEFAULT_RESPONSE_BODY))
        val result = apiClient.rawPost(request, DEFAULT_REQUEST_BODY.toByteArray())

        // assert on result parsed by ApiClient
        Assert.assertEquals(DEFAULT_RESPONSE_BODY, result)

        // assert on request received by mock server
        val delivered = server.takeRequest()
        assertRequestContents(delivered)
        Assert.assertEquals(DEFAULT_REQUEST_BODY, delivered.body.readUtf8())
    }

    @Test
    fun test200ResponseCompressed() {
        server.enqueue(MockResponse().setBody(DEFAULT_RESPONSE_BODY))
        val result = apiClient.post(request, DEFAULT_REQUEST_BODY.toByteArray())

        // assert on result parsed by ApiClient
        Assert.assertEquals(DEFAULT_RESPONSE_BODY, result)

        // assert on request received by mock server
        val delivered = server.takeRequest()
        assertRequestContents(delivered)
        Assert.assertEquals(DEFAULT_REQUEST_BODY, delivered.readCompressedRequestBody())
    }

    @Test(expected = RuntimeException::class)
    fun test400Response() {
        server.enqueue(MockResponse().setBody(DEFAULT_RESPONSE_BODY).setResponseCode(400))
        apiClient.rawPost(request, DEFAULT_REQUEST_BODY.toByteArray())
    }

    @Test(expected = RuntimeException::class)
    fun test500Response() {
        server.enqueue(MockResponse().setBody(DEFAULT_RESPONSE_BODY).setResponseCode(500))
        apiClient.rawPost(request, DEFAULT_REQUEST_BODY.toByteArray())
    }

    @Test(expected = RuntimeException::class)
    fun testClientSideConnectionTimeout() {
        apiClient.timeoutMs = 1000
        apiClient.rawPost(request, DEFAULT_REQUEST_BODY.toByteArray())
    }

    /**
     * Sends a large session payload & verifies that the server receives the expected JSON. The
     * large payload helps assert that buffering & GZIP compression work as expected.
     */
    @Test
    fun testSendLargePayload() {
        server.enqueue(MockResponse().setBody(DEFAULT_RESPONSE_BODY))

        val payload = createLargeSessionPayload()
        val result = apiClient.post(request, payload.toByteArray())

        // assert on result parsed by ApiClient
        Assert.assertEquals(DEFAULT_RESPONSE_BODY, result)

        // assert on request received by mock server
        val delivered = server.takeRequest()
        val observed = delivered.readCompressedRequestBody()
        Assert.assertEquals(payload, observed)
    }

    /**
     * Simulates an I/O exception midway through a request.
     */
    @Test(expected = RuntimeException::class)
    fun testIoExceptionMidRequest() {
        server.enqueue(MockResponse().throttleBody(1, 1000, TimeUnit.MILLISECONDS))

        // shutdown the server mid-request
        Executors.newSingleThreadScheduledExecutor().schedule({
            server.shutdown()
        }, 25, TimeUnit.MILLISECONDS)

        // fire off the api request
        apiClient.rawPost(request, DEFAULT_REQUEST_BODY.toByteArray())
    }

    @Test
    fun testAllRequestHeadersSet() {
        request = ApiRequest(
            "application/json",
            "Embrace/a/1",
            "application/json",
            "application/json",
            "application/json",
            "abcde",
            "test_did",
            "test_eid",
            "test_lid",
            EmbraceUrl.getUrl(baseUrl)
        )
        server.enqueue(MockResponse().setBody(DEFAULT_RESPONSE_BODY))
        apiClient.rawPost(request, DEFAULT_REQUEST_BODY.toByteArray())

        // assert all request headers were set
        val delivered = server.takeRequest()
        val headers = delivered.headers.toMap()
            .minus("Host")
        Assert.assertEquals(
            mapOf(
                "Accept" to "application/json",
                "User-Agent" to "Embrace/a/1",
                "Content-Type" to "application/json",
                "Content-Encoding" to "application/json",
                "Accept-Encoding" to "application/json",
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

    private fun RecordedRequest.readCompressedRequestBody(): String {
        val inputStream = body.inputStream()
        return GZIPInputStream(inputStream).bufferedReader().readText()
    }

    private fun assertRequestContents(delivered: RecordedRequest) {
        Assert.assertEquals("POST", delivered.method)
        Assert.assertEquals("/test", delivered.path)
        val headers = delivered.headers.toMap()
            .minus("Host")
        Assert.assertEquals(
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

    private fun createLargeSessionPayload(): String {
        val props = (1..5000).associate { "my_big_key_$it" to "my_big_val_$it" }
        val session = fakeSession().copy(properties = props)
        return Gson().toJson(session)
    }
}

private const val DEFAULT_RESPONSE_BODY = "{}"
private const val DEFAULT_REQUEST_BODY = "{}"
