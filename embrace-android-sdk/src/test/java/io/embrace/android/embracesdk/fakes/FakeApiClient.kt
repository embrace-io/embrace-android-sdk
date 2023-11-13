package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiResponse
import java.util.LinkedList
import java.util.Queue

internal class FakeApiClient : ApiClient {
    val sentRequests: MutableList<Pair<ApiRequest, ByteArray?>> = mutableListOf()
    private val queuedResponses: Queue<ApiResponse<String>> = LinkedList()

    override fun executeGet(request: ApiRequest): ApiResponse<String> = getNext(request, null)

    override fun executePost(request: ApiRequest, payloadToCompress: ByteArray): ApiResponse<String> = getNext(request, payloadToCompress)

    fun queueResponse(response: ApiResponse<String>) {
        queuedResponses.add(response)
    }

    private fun getNext(request: ApiRequest, bytes: ByteArray?): ApiResponse<String> {
        sentRequests.add(Pair(request, bytes))
        return checkNotNull(queuedResponses.poll()) { "No response" }
    }
}
