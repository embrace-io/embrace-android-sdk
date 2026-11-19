package io.embrace.android.testapiserver

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A fake of Embrace's server backend serving one of its APIs, [api], as the dispatcher of a [MockWebServer]. Each
 * request is recorded under the endpoint [FakeBackendApi.endpointOf] finds for it, and answered with the next response
 * [enqueueResponse] queued for that endpoint, or else by [FakeBackendApi.respond]. A request for none of the API's
 * endpoints gets 404.
 */
class FakeApiServer<E : Any>(
    private val api: FakeBackendApi<E>,
) : Dispatcher() {

    private val received = ConcurrentHashMap<E, CopyOnWriteArrayList<RecordedRequest>>()
    private val queued = ConcurrentHashMap<E, ConcurrentLinkedQueue<MockResponse>>()
    private val counts = ConcurrentHashMap<String, Int>()

    /** How many requests it answered, keyed by `<method> <path> <status>`, the path without its query. */
    val answered: Map<String, Int> get() = counts.toSortedMap()

    /** The requests for [endpoint], in the order they arrived. */
    fun fetchRequests(endpoint: E): List<RecordedRequest> = received[endpoint]?.toList().orEmpty()

    /** Answers the next request for [endpoint] with [response]; one endpoint's queued responses go out in order. */
    fun enqueueResponse(endpoint: E, response: MockResponse) {
        queued.getOrPut(endpoint, ::ConcurrentLinkedQueue).add(response)
    }

    /** Forgets every request it received, everything it answered and every response still queued. */
    fun reset() {
        received.clear()
        queued.clear()
        counts.clear()
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val endpoint = api.endpointOf(request)
        val response = if (endpoint == null) {
            MockResponse().setResponseCode(HTTP_NOT_FOUND)
        } else {
            received.getOrPut(endpoint, ::CopyOnWriteArrayList).add(request)
            queued[endpoint]?.poll() ?: api.respond(endpoint, request)
        }
        val path = request.requestUrl?.encodedPath ?: request.path?.substringBefore('?')
        counts.merge("${request.method} $path ${response.statusCode()}", 1, Int::plus)
        return response
    }
}

/** The status code of a response, from its status line (`HTTP/1.1 200 OK`). */
private fun MockResponse.statusCode(): Int = status.split(' ')[1].toInt()

private const val HTTP_NOT_FOUND = 404
