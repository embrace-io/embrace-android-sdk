package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiResponse
import java.util.LinkedList
import java.util.Queue

internal class FakeApiClient : ApiClient {
    val sentRequests: MutableList<Pair<ApiRequest, ByteArray?>> = mutableListOf()
    private val queuedResponses: Queue<ApiResponse> = LinkedList()

    override fun executeGet(request: ApiRequest): ApiResponse = getNext(request, null)

    override fun executePost(request: ApiRequest, payloadToCompress: ByteArray): ApiResponse = getNext(request, payloadToCompress)

    fun queueResponse(response: ApiResponse) {
        queuedResponses.add(response)
    }

    private fun getNext(request: ApiRequest, bytes: ByteArray?): ApiResponse {
        sentRequests.add(Pair(request, bytes))
        return checkNotNull(queuedResponses.poll()) { "No response" }
    }
}
