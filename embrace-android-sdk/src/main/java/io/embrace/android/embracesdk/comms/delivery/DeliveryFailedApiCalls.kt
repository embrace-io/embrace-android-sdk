package io.embrace.android.embracesdk.comms.delivery

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A queue containing failed API calls.
 */
internal class DeliveryFailedApiCalls : ConcurrentLinkedQueue<DeliveryFailedApiCall>()

/**
 * A failed API call.
 */
internal data class DeliveryFailedApiCall(
    @SerializedName("apiRequest") val apiRequest: ApiRequest,
    @SerializedName("cachedPayload") val cachedPayloadFilename: String,
    @SerializedName("queueTime") val queueTime: Long? = null
)

/**
 * A map containing a list of failed API calls per endpoint.
 */
internal class FailedApiCallsPerEndpoint : ConcurrentHashMap<Endpoint, DeliveryFailedApiCalls>() {

    /**
     * Adds a failed API call in the corresponding endpoint's list.
     */
    fun add(endpoint: Endpoint, failedApiCall: DeliveryFailedApiCall) {
        if (this.containsKey(endpoint)) {
            this[endpoint]?.add(failedApiCall)
        } else {
            val failedApiCalls = DeliveryFailedApiCalls()
            failedApiCalls.add(failedApiCall)
            this[endpoint] = failedApiCalls
        }
    }

    /**
     * Returns the total number of failed API calls in all endpoints' lists.
     */
    fun failedApiCallsCount() = this.values.sumOf { it.size }

    /**
     * Returns the number of failed API calls in the corresponding endpoint's list.
     */
    fun failedApiCallsCount(endpoint: Endpoint) = this[endpoint]?.size ?: 0

    /**
     * Returns true if there are any failed API calls in any endpoint's list.
     */
    fun hasAnyFailedApiCalls(): Boolean {
        return this.values.any { it.isNotEmpty() }
    }

    /**
     * Returns true if there are no failed API calls in any endpoint's list.
     */
    fun hasNoFailedApiCalls(): Boolean {
        return !hasAnyFailedApiCalls()
    }

    /**
     * Returns the next failed API call to be sent and removes it from the corresponding
     * endpoint's list.
     */
    fun pollNextFailedApiCall(): DeliveryFailedApiCall? {
        this[Endpoint.SESSIONS]?.let { sessions ->
            if (sessions.isNotEmpty()) {
                return sessions.poll()
            }
        }

        val entryToPollFrom = this
            .entries
            .filter { it.value.isNotEmpty() }
            .minByOrNull { it.value.peek()?.queueTime ?: Long.MAX_VALUE }
            ?.key

        return this[entryToPollFrom]?.poll()
    }
}
