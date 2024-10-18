package io.embrace.android.embracesdk.internal.network.logging

/**
 * Included in the payload when the network request capture limit has been exceeded for a
 * particular domain. Specifies the limit, and the total count.
 */
data class DomainCount(
    /** The total count of network calls for the given domain. */
    val requestCount: Int,
    /** The configured request capture limit for the given domain. */
    val captureLimit: Int
)
