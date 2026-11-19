package io.embrace.android.testapiserver

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

/**
 * One API of Embrace's server backend, as a [FakeApiServer] fakes it: which of the API's endpoints, of type [E], a
 * request calls, and how each endpoint answers. Either may throw instead, to fail on a request the fake must never get.
 */
interface FakeBackendApi<E : Any> {

    /** The endpoint [request] calls, or null when it calls none of them; [FakeApiServer] answers that with 404. */
    fun endpointOf(request: RecordedRequest): E?

    /** The answer to [request] on [endpoint] when no response is queued for it; by default 200 with no body. */
    fun respond(endpoint: E, request: RecordedRequest): MockResponse = MockResponse()
}
