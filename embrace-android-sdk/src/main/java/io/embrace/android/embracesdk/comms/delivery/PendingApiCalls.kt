package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint
import java.util.concurrent.ConcurrentHashMap

/**
 * A map containing a queue of pending API calls for each endpoint.
 */
internal class PendingApiCalls {

    private val pendingApiCallsMap = ConcurrentHashMap<Endpoint, PendingApiCallsQueue>()

    @Transient
    private var rateLimitHandler: RateLimitHandler? = null

    /**
     * Adds a pending API call in the corresponding endpoint's queue.
     * If the endpoint's queue has reached its limit, the oldest pending API call is removed and
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
     * Sets the [RateLimitHandler] to be used to determine if a queue is rate limited or not.
     */
    fun setRateLimitHandler(rateLimitHandler: RateLimitHandler) {
        this.rateLimitHandler = rateLimitHandler
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
                    return sessionsQueue.poll()
                }
            }
        }

        // Obtain the queue with oldest pending API call that is not rate limited.
        val queueWithMinTime = pendingApiCallsMap
            .entries
            .filter { it.value.hasPendingApiCallsToSend(it.key) }
            .minByOrNull { it.value.peek()?.queueTime ?: Long.MAX_VALUE }
            ?.value

        return queueWithMinTime?.let { queue ->
            synchronized(queue) {
                queue.poll()
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
    private fun PendingApiCallsQueue.hasPendingApiCallsToSend(endpoint: Endpoint): Boolean {
        return this.isNotEmpty() && rateLimitHandler?.isRateLimited(endpoint) != true
    }

    /**
     * Returns true if the endpoint's queue has reached its limit.
     */
    private fun PendingApiCallsQueue.hasReachedLimit(endpoint: Endpoint): Boolean {
        return this.size >= endpoint.getMaxPendingApiCalls()
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
