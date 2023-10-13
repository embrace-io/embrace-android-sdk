package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal data class NetworkSessionV2(
    /** The list of network requests captured as part of the session. */
    @SerializedName("r") val requests: List<NetworkCallV2>,
    /** Counts of network requests per domain, only for domains exceeding the capture limit. */
    @SerializedName("c") val requestCounts: Map<String, DomainCount>
) {
    /**
     * Included in the payload when the network request capture limit has been exceeded for a
     * particular domain. Specifies the limit, and the total count.
     */
    internal data class DomainCount(
        /** The total count of network calls for the given domain. */
        val requestCount: Int,
        /** The configured request capture limit for the given domain. */
        val captureLimit: Int
    )
}
