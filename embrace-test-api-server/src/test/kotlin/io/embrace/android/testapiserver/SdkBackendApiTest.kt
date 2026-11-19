package io.embrace.android.testapiserver

import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

internal class SdkBackendApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: FakeApiServer<SdkBackendEndpoint>
    private lateinit var client: HttpTestClient
    private val seen = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        api = FakeApiServer(
            SdkBackendApi(RemoteConfigEndpoint(CONFIG, ETAG)) { endpoint, request ->
                seen.add("$endpoint ${request.path}")
            },
        )
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
    fun `each SDK endpoint is found by its method and path under any base URL, and nothing else is`() {
        client.call("GET", "/api/v2/config?appId=abcde&deviceId=d")
        client.call("GET", "/v2/config")
        assertEquals(200, client.call("POST", "/api/v2/spans").code)
        assertEquals(200, client.call("POST", "/v2/logs").code)
        assertEquals(200, client.call("POST", "/api/v2/attachments").code)
        listOf("GET /v2/spans", "POST /v2/config", "GET /v1/config", "GET /v2/configs", "POST /v2/other").forEach {
            val (method, path) = it.split(' ')
            assertEquals(it, 404, client.call(method, path).code)
        }

        assertEquals(listOf("/api/v2/config?appId=abcde&deviceId=d", "/v2/config"), paths(SdkBackendEndpoint.CONFIG))
        assertEquals(listOf("/api/v2/spans"), paths(SdkBackendEndpoint.SESSIONS))
        assertEquals(listOf("/v2/logs"), paths(SdkBackendEndpoint.LOGS))
        assertEquals(listOf("/api/v2/attachments"), paths(SdkBackendEndpoint.ATTACHMENTS))
        assertEquals(
            listOf(
                "CONFIG /api/v2/config?appId=abcde&deviceId=d",
                "CONFIG /v2/config",
                "SESSIONS /api/v2/spans",
                "LOGS /v2/logs",
                "ATTACHMENTS /api/v2/attachments",
            ),
            seen,
        )
    }

    @Test
    fun `a config fetch gets the config gzipped with its ETag, and 304 once it sends that ETag`() {
        val first = client.call("GET", "/v2/config?appId=abcde")
        assertEquals(200, first.code)
        assertEquals("application/json", first.headers["Content-Type"])
        assertEquals("gzip", first.headers["Content-Encoding"])
        assertEquals(ETAG, first.headers["ETag"])
        assertEquals(CONFIG, first.decompressedBody())

        val refetch = client.call("GET", "/v2/config?appId=abcde", "If-None-Match" to ETAG)
        assertEquals(304, refetch.code)
        assertEquals(ETAG, refetch.headers["ETag"])
        assertEquals(0, refetch.body.size)

        val otherConfig = client.call("GET", "/v2/config", "If-None-Match" to "\"another\"")
        assertEquals(200, otherConfig.code)
        assertEquals(CONFIG, otherConfig.decompressedBody())
    }

    private fun paths(endpoint: SdkBackendEndpoint) = api.fetchRequests(endpoint).map { it.path }

    private companion object {
        const val CONFIG = "{\"user_session\":{\"inactivity_timeout_seconds\":30}}"
        const val ETAG = "\"config-etag\""
    }
}
