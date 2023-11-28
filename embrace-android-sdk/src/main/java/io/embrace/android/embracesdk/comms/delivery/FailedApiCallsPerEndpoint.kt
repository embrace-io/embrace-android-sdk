package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint
import java.util.concurrent.ConcurrentHashMap

/**
 * A map containing a list of failed API calls per endpoint.
 * Public methods on this class aren't meant to be accessed by multiple threads.
 */
internal class FailedApiCallsPerEndpoint {

    private val failedApiCallsMap = ConcurrentHashMap<Endpoint, DeliveryFailedApiCalls>()

    /**
     * Adds a failed API call in the corresponding endpoint's list.
     */
    fun add(failedApiCall: DeliveryFailedApiCall) {
        val endpoint = failedApiCall.apiRequest.url.endpoint()
        val failedApiCallsForEndpoint = failedApiCallsMap.getOrPut(endpoint) { DeliveryFailedApiCalls() }
        failedApiCallsForEndpoint.add(failedApiCall)
    }

    /**
     * Returns the next failed API call to be sent and removes it from the corresponding
     * endpoint's list.
     */
    fun pollNextFailedApiCall(): DeliveryFailedApiCall? {
        failedApiCallsMap[Endpoint.SESSIONS]?.let { sessions ->
            if (sessions.isNotEmpty()) {
                return sessions.poll()
            }
        }

        val entryToPollFrom = failedApiCallsMap
            .entries
            .filter { it.value.isNotEmpty() }
            .minByOrNull { it.value.peek()?.queueTime ?: Long.MAX_VALUE }
            ?.key

        return entryToPollFrom?.let {
            failedApiCallsMap[it]?.poll()
        }
    }

    /**
     * Returns true if the number of retries for the endpoint is below the limit.
     */
    fun isBelowRetryLimit(endpoint: Endpoint): Boolean {
        val failedApiCallsCount = failedApiCallsMap[endpoint]?.size ?: 0
        return failedApiCallsCount < endpoint.getMaxFailedApiCalls()
    }

    /**
     * Clears all lists of failed API calls.
     */
    fun clear() {
        failedApiCallsMap.clear()
    }

    /**
     * Returns true if there are any failed API calls in any endpoint's list.
     */
    fun hasAnyFailedApiCalls(): Boolean {
        return failedApiCallsMap.values.any { it.isNotEmpty() }
    }

    /**
     * Returns true if there are no failed API calls in any endpoint's list.
     */
    fun hasNoFailedApiCalls(): Boolean {
        return !hasAnyFailedApiCalls()
    }

    private fun Endpoint.getMaxFailedApiCalls(): Int {
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
