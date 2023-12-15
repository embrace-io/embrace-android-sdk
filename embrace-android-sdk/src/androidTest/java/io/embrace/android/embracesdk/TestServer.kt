package io.embrace.android.embracesdk

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

/**
 * Server used to mock responses of different endpoints.
 * It wraps MockWebServer and uses it to act as a mock server.
 * To work properly, the sdk needs to set its baseUrl to the one returned by this class.
 */
public class TestServer {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockNetworkResponses: MutableMap<String, TestServerResponse>

    public fun start(responses: Map<String, TestServerResponse>) {
        mockWebServer = MockWebServer()
        mockNetworkResponses = responses.toMutableMap()
        setDispatcher()
        mockWebServer.start()
    }

    public fun stop() {
        mockWebServer.shutdown()
    }

    /**
     * Gets the baseUrl that is needed to be set in the SDK so that it works with this test server.
     */
    public fun getBaseUrl(): String = mockWebServer.url("").toString().removeSuffix("/")

    /**
     * Gets the next request to be consumed. If there is no request, it will return null
     * after REQUEST_TIMEOUT_SECONDS.
     */
    public fun takeRequest(): RecordedRequest? {
        return mockWebServer.takeRequest(REQUEST_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    }

    /**
     * Adds a mocked response to be returned when calling a specific endpoint.
     */
    public fun addResponse(endpoint: EmbraceEndpoint, response: TestServerResponse) {
        mockNetworkResponses[endpoint.url] = response
    }

    /**
     * Returns a specific response for each endpoint.
     */
    private fun setDispatcher() {
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val endpoint = request.path?.substringBefore("?")
                val testServerResponse =
                    mockNetworkResponses[endpoint]
                        ?: TestServerResponse(HttpURLConnection.HTTP_NOT_FOUND)

                return testServerResponse.toMockWebServerResponse()
            }
        }
    }
}

/**
 * Mock network response to be delivered when calling an endpoint.
 */
public data class TestServerResponse(val statusCode: Int, val body: String = "") {
    public fun toMockWebServerResponse(): MockResponse {
        return MockResponse().setResponseCode(statusCode).also {
            if (body.isNotEmpty()) it.setBody(body)
        }
    }
}

public const val REQUEST_TIMEOUT_MILLISECONDS: Long = 60000L
