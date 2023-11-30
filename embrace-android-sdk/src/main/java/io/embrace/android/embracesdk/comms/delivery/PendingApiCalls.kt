package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint
import java.util.concurrent.ConcurrentHashMap

/**
 * A map containing a list of pending API calls per endpoint.
 * Public methods on this class aren't meant to be accessed by multiple threads.
 */
internal class PendingApiCalls {

    private val pendingApiCallsMap = ConcurrentHashMap<Endpoint, PendingApiCallsQueue>()

    /**
     * Adds a pending API call in the corresponding endpoint's list.
     */
    fun add(pendingApiCall: PendingApiCall) {
        val endpoint = pendingApiCall.apiRequest.url.endpoint()
        val pendingApiCallsForEndpoint = pendingApiCallsMap.getOrPut(endpoint) { PendingApiCallsQueue() }
        pendingApiCallsForEndpoint.add(pendingApiCall)
    }

    /**
     * Returns the next pending API call to be sent and removes it from the corresponding
     * endpoint's list.
     */
    fun pollNextPendingApiCall(): PendingApiCall? {
        pendingApiCallsMap[Endpoint.SESSIONS]?.let { sessions ->
            if (sessions.isNotEmpty()) {
                return sessions.poll()
            }
        }

        val entryToPollFrom = pendingApiCallsMap
            .entries
            .filter { it.value.isNotEmpty() }
            .minByOrNull { it.value.peek()?.queueTime ?: Long.MAX_VALUE }
            ?.key

        return entryToPollFrom?.let {
            pendingApiCallsMap[it]?.poll()
        }
    }

    /**
     * Returns true if the number of retries for the endpoint is below the limit.
     */
    fun isBelowRetryLimit(endpoint: Endpoint): Boolean {
        val pendingApiCallsCount = pendingApiCallsMap[endpoint]?.size ?: 0
        return pendingApiCallsCount < endpoint.getMaxPendingApiCalls()
    }

    /**
     * Clears all lists of pending API calls.
     */
    fun clear() {
        pendingApiCallsMap.clear()
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
