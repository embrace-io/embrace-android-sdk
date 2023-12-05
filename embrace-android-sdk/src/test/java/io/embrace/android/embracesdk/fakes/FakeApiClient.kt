package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiResponse
import java.io.ByteArrayInputStream
import java.util.LinkedList
import java.util.Queue

internal class FakeApiClient : ApiClient {
    val sentRequests: MutableList<Pair<ApiRequest, ByteArrayInputStream?>> = mutableListOf()
    private val queuedResponses: Queue<ApiResponse> = LinkedList()

    override fun executeGet(request: ApiRequest): ApiResponse = getNext(request, null)

    override fun executePost(request: ApiRequest, payloadStream: ByteArrayInputStream): ApiResponse = getNext(request, payloadStream)

    fun queueResponse(response: ApiResponse) {
        queuedResponses.add(response)
    }

    private fun getNext(request: ApiRequest, bytes: ByteArrayInputStream?): ApiResponse {
        sentRequests.add(Pair(request, bytes))
        return checkNotNull(queuedResponses.poll()) { "No response" }
    }
}
