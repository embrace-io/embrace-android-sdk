package io.embrace.android.gradle.integration.framework

import io.embrace.android.gradle.plugin.network.EmbraceEndpoint
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.LinkedList

class FakeApiServer : Dispatcher() {

    private val endpoints = EmbraceEndpoint.values().associateBy(EmbraceEndpoint::url)
    private val receivedRequests = EmbraceEndpoint.values().associateWith {
        mutableListOf<RecordedRequest>()
    }
    private val enqueuedResponses = EmbraceEndpoint.values().associateWith {
        LinkedList<MockResponse>()
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path?.substringAfter("/api")
        require(request.method == "POST")
        val endpoint = endpoints[path] ?: error("Unexpected request path: ${request.path}")
        checkNotNull(receivedRequests[endpoint]).add(request)

        // poll for any specific responses, otherwise just return a default 200
        val responses = checkNotNull(enqueuedResponses[endpoint])
        return responses.poll() ?: MockResponse().setResponseCode(200)
    }

    fun fetchRequests(endpoint: EmbraceEndpoint): List<RecordedRequest> {
        return receivedRequests[endpoint]?.toList() ?: emptyList()
    }

    fun enqueueResponse(endpoint: EmbraceEndpoint, response: MockResponse) {
        enqueuedResponses[endpoint]?.add(response)
    }
}
