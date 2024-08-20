package io.embrace.android.embracesdk.internal.network.logging

public interface DomainCountLimiter {
    /**
     * Determines if a network request can be logged based on the domain limits.
     *
     * @param domain the domain of the network request
     * @return true if the network request should be logged, false otherwise
     */
    public fun canLogNetworkRequest(domain: String): Boolean
}
