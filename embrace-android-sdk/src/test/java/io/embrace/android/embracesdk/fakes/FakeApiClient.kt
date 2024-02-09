package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.utils.SerializationAction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.LinkedList
import java.util.Queue

internal class FakeApiClient : ApiClient {
    val sentRequests: MutableList<Pair<ApiRequest, ByteArrayInputStream?>> = mutableListOf()
    private val queuedResponses: Queue<ApiResponse> = LinkedList()

    override fun executeGet(request: ApiRequest): ApiResponse = getNext(request) {}

    override fun executePost(request: ApiRequest, action: SerializationAction): ApiResponse = getNext(request, action)

    fun queueResponse(response: ApiResponse) {
        queuedResponses.add(response)
    }

    private fun getNext(request: ApiRequest, action: SerializationAction): ApiResponse {
        val stream = ByteArrayOutputStream()
        action(stream)
        val bytes = ByteArrayInputStream(stream.toByteArray())
        sentRequests.add(Pair(request, bytes))
        return checkNotNull(queuedResponses.poll()) { "No response" }
    }
}
