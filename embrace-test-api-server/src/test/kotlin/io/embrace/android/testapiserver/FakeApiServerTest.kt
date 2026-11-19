package io.embrace.android.testapiserver

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class FakeApiServerTest {

    private lateinit var api: FakeApiServer<TestEndpoint>
    private lateinit var server: MockWebServer
    private lateinit var client: HttpTestClient

    @Before
    fun setUp() {
        api = FakeApiServer(TestBackendApi())
        server = MockWebServer().apply {
            dispatcher = api
            start()
        }
        client = HttpTestClient(server)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `requests are recorded under the endpoint they call, in order, and answered by it`() {
        assertEquals(201, client.call("POST", "/a/1").code)
        assertEquals(202, client.call("GET", "/b").code)
        assertEquals(201, client.call("GET", "/a/2?x=y").code)
        assertEquals(404, client.call("GET", "/c").code)

        assertEquals(listOf("/a/1", "/a/2?x=y"), api.fetchRequests(TestEndpoint.A).map(RecordedRequest::path))
        assertEquals(listOf("/b"), api.fetchRequests(TestEndpoint.B).map(RecordedRequest::path))
        assertEquals(
            mapOf("GET /a/2 201" to 1, "GET /b 202" to 1, "GET /c 404" to 1, "POST /a/1 201" to 1),
            api.answered,
        )
    }

    @Test
    fun `an endpoint's queued responses answer its next requests in order, then its own answer does`() {
        api.enqueueResponse(TestEndpoint.A, MockResponse().setResponseCode(500))
        api.enqueueResponse(TestEndpoint.A, MockResponse().setResponseCode(400))

        assertEquals(202, client.call("GET", "/b").code)
        assertEquals(500, client.call("GET", "/a").code)
        assertEquals(400, client.call("GET", "/a").code)
        assertEquals(201, client.call("GET", "/a").code)
    }

    @Test
    fun `a reset forgets requests, counts and queued responses`() {
        api.enqueueResponse(TestEndpoint.B, MockResponse().setResponseCode(500))
        client.call("GET", "/a")
        api.reset()

        assertEquals(emptyList<RecordedRequest>(), api.fetchRequests(TestEndpoint.A))
        assertEquals(emptyMap<String, Int>(), api.answered)
        assertEquals(202, client.call("GET", "/b").code)
    }

    @Test
    fun `by default an endpoint answers 200`() {
        val defaults = FakeApiServer(
            object : FakeBackendApi<TestEndpoint> {
                override fun endpointOf(request: RecordedRequest) = TestEndpoint.A
            },
        )
        server.dispatcher = defaults

        assertEquals(200, client.call("GET", "/anything").code)
        assertEquals(1, defaults.fetchRequests(TestEndpoint.A).size)
    }

    private enum class TestEndpoint(val path: String, val status: Int) {
        A("/a", 201),
        B("/b", 202),
    }

    /** Finds an endpoint by its path prefix, and answers with its status. */
    private class TestBackendApi : FakeBackendApi<TestEndpoint> {

        override fun endpointOf(request: RecordedRequest): TestEndpoint? =
            TestEndpoint.entries.firstOrNull { request.path?.startsWith(it.path) == true }

        override fun respond(endpoint: TestEndpoint, request: RecordedRequest): MockResponse =
            MockResponse().setResponseCode(endpoint.status)
    }
}
