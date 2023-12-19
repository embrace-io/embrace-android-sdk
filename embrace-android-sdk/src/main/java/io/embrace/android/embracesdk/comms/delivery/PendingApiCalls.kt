package io.embrace.android.embracesdk.comms.delivery

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint
import java.util.concurrent.ConcurrentHashMap

/**
 * A map containing a queue of pending API calls for each endpoint.
 */
internal class PendingApiCalls(
    @SerializedName("pendingApiCallsMap")
    internal val pendingApiCallsMap: MutableMap<Endpoint, MutableList<PendingApiCall>> = ConcurrentHashMap<
        Endpoint, MutableList<PendingApiCall>>()
) {

    /**
     * Adds a pending API call in the corresponding endpoint's queue.
     * If the endpoint's queue has reached its limit, the oldest pending API call is removed and
     * the new one is added.
     */
    fun add(pendingApiCall: PendingApiCall) {
        val endpoint = pendingApiCall.apiRequest.url.endpoint()
        val pendingApiCallsForEndpoint = pendingApiCallsMap.getOrPut(endpoint, ::mutableListOf)

        synchronized(pendingApiCallsForEndpoint) {
            if (pendingApiCallsForEndpoint.hasReachedLimit(endpoint)) {
                pendingApiCallsForEndpoint.removeFirstOrNull()
            }
            pendingApiCallsForEndpoint.add(pendingApiCall)
        }
    }

    /**
     * Returns the next pending API call to be sent and removes it from the corresponding
     * endpoint's queue.
     */
    fun pollNextPendingApiCall(): PendingApiCall? {
        pendingApiCallsMap[Endpoint.SESSIONS]?.let { sessionsQueue ->
            synchronized(sessionsQueue) {
                if (sessionsQueue.isNotEmpty()) {
                    return sessionsQueue.removeFirstOrNull()
                }
            }
        }

        val queueWithMinTime = pendingApiCallsMap
            .entries
            .filter { it.value.isNotEmpty() }
            .minByOrNull { it.value.firstOrNull()?.queueTime ?: Long.MAX_VALUE }
            ?.value

        return queueWithMinTime?.let { queue ->
            synchronized(queue) {
                queue.removeFirstOrNull()
            }
        }
    }

    /**
     * Returns true if the endpoint's queue has reached its limit.
     */
    private fun MutableList<PendingApiCall>.hasReachedLimit(endpoint: Endpoint): Boolean {
        return this.size >= endpoint.getMaxPendingApiCalls()
    }

    /**
     * Returns true if there is al least one pending API call in any endpoint's queue.
     */
    fun hasAnyPendingApiCall(): Boolean {
        return pendingApiCallsMap.values.any { it.isNotEmpty() }
    }

    private fun Endpoint.getMaxPendingApiCalls(): Int {
        return when (this) {
            Endpoint.EVENTS -> 100
            Endpoint.BLOBS -> 50
            Endpoint.LOGGING -> 100
            Endpoint.NETWORK -> 50
            Endpoint.SESSIONS -> 100
            Endpoint.UNKNOWN -> 50
        }
    }
}
