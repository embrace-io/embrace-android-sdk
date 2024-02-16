package io.embrace.android.embracesdk.comms.delivery

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.comms.api.Endpoint
import java.util.concurrent.ConcurrentHashMap

/**
 * A map containing a queue of pending API calls for each endpoint.
 */
@JsonClass(generateAdapter = true)
internal class PendingApiCalls(
    @Json(name = "pendingApiCallsMap")
    internal val pendingApiCallsMap: MutableMap<Endpoint, MutableList<PendingApiCall>> =
        ConcurrentHashMap<Endpoint, MutableList<PendingApiCall>>()
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
     * Prioritizes session API calls over others and then oldest pending API calls over newer ones.
     */
    fun pollNextPendingApiCall(): PendingApiCall? {
        // Return a session API call if it is not rate limited and it is not empty.
        pendingApiCallsMap[Endpoint.SESSIONS]?.let { sessionsQueue ->
            synchronized(sessionsQueue) {
                if (sessionsQueue.hasPendingApiCallsToSend(Endpoint.SESSIONS)) {
                    return sessionsQueue.removeFirstOrNull()
                }
            }
        }

        // Obtain the queue with oldest pending API call that is not rate limited.
        val queueWithMinTime = pendingApiCallsMap
            .entries
            .filter { it.value.hasPendingApiCallsToSend(it.key) }
            .minByOrNull { it.value.firstOrNull()?.queueTime ?: Long.MAX_VALUE }
            ?.value

        return queueWithMinTime?.let { queue ->
            synchronized(queue) {
                queue.removeFirstOrNull()
            }
        }
    }

    /**
     * Returns true if there is al least one pending API call in any non rate limited queue.
     */
    fun hasPendingApiCallsToSend(): Boolean {
        return pendingApiCallsMap.entries.any {
            it.value.hasPendingApiCallsToSend(it.key)
        }
    }

    /**
     * Returns true if the queue has at least one pending API call and the endpoint is not rate limited.
     */
    private fun List<PendingApiCall>.hasPendingApiCallsToSend(endpoint: Endpoint): Boolean {
        return this.isNotEmpty() && !endpoint.isRateLimited
    }

    /**
     * Returns true if the endpoint's queue has reached its limit.
     */
    private fun List<PendingApiCall>.hasReachedLimit(endpoint: Endpoint): Boolean {
        return this.size >= endpoint.getMaxPendingApiCalls()
    }

    private fun Endpoint.getMaxPendingApiCalls(): Int {
        return when (this) {
            Endpoint.EVENTS -> 100
            Endpoint.BLOBS -> 50
            Endpoint.LOGGING -> 100
            Endpoint.LOGS -> 10
            Endpoint.NETWORK -> 50
            Endpoint.SESSIONS -> 100
            Endpoint.UNKNOWN -> 50
        }
    }
}
