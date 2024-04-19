package io.embrace.android.embracesdk.network.logging

internal interface DomainCountLimiter {
    /**
     * Determines if a network request can be logged based on the domain limits.
     *
     * @param domain the domain of the network request
     * @return true if the network request should be logged, false otherwise
     */
    fun canLogNetworkRequest(domain: String): Boolean
}