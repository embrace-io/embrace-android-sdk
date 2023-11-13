package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiResponse
import java.util.LinkedList
import java.util.Queue

internal class FakeApiClient : ApiClient {
    private val queuedResponses: Queue<ApiResponse<String>> = LinkedList()

    override fun executeGet(request: ApiRequest): ApiResponse<String> = getNext()

    override fun executePost(request: ApiRequest, payloadToCompress: ByteArray): ApiResponse<String> = getNext()

    fun queueResponse(response: ApiResponse<String>) {
        queuedResponses.add(response)
    }

    private fun getNext(): ApiResponse<String> = checkNotNull(queuedResponses.poll()) { "No response" }
}
