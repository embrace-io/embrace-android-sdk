package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint
import java.util.concurrent.ConcurrentHashMap

/**
 * A map containing a list of pending API calls per endpoint.
 */
internal class PendingApiCalls {

    private val pendingApiCallsMap = ConcurrentHashMap<Endpoint, PendingApiCallsQueue>()

    /**
     * Adds a pending API call in the corresponding endpoint's list.
     * If the endpoint's list has reached its limit, the oldest pending API call is removed and
     * the new one is added.
     */
    fun add(pendingApiCall: PendingApiCall) {
        val endpoint = pendingApiCall.apiRequest.url.endpoint()
        val pendingApiCallsForEndpoint = pendingApiCallsMap.getOrPut(endpoint) { PendingApiCallsQueue() }

        synchronized(pendingApiCallsForEndpoint) {
            if (pendingApiCallsForEndpoint.hasReachedLimit(endpoint)) {
                pendingApiCallsForEndpoint.remove()
            }
            pendingApiCallsForEndpoint.add(pendingApiCall)
        }
    }

    /**
     * Returns the next pending API call to be sent and removes it from the corresponding
     * endpoint's list.
     */
    fun pollNextPendingApiCall(): PendingApiCall? {
        pendingApiCallsMap[Endpoint.SESSIONS]?.let { sessionsQueue ->
            synchronized(sessionsQueue) {
                if (sessionsQueue.isNotEmpty()) {
                    return sessionsQueue.poll()
                }
            }
        }

        val queueWithMinTime = pendingApiCallsMap
            .entries
            .filter { it.value.isNotEmpty() }
            .minByOrNull { it.value.peek()?.queueTime ?: Long.MAX_VALUE }
            ?.value

        return queueWithMinTime?.let { queue ->
            synchronized(queue) {
                queue.poll()
            }
        }
    }

    /**
     * Returns true if the endpoint's list has reached its limit.
     */
    private fun PendingApiCallsQueue.hasReachedLimit(endpoint: Endpoint): Boolean {
        return this.size >= endpoint.getMaxPendingApiCalls()
    }

    /**
     * Returns true if there is al least one pending API call in any endpoint's list.
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
