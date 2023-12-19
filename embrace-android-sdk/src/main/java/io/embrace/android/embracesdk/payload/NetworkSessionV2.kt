package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class NetworkSessionV2(
    /** The list of network requests captured as part of the session. */
    @Json(name = "r") val requests: List<NetworkCallV2>,
    /** Counts of network requests per domain, only for domains exceeding the capture limit. */
    @Json(name = "c") val requestCounts: Map<String, DomainCount>
) {
    /**
     * Included in the payload when the network request capture limit has been exceeded for a
     * particular domain. Specifies the limit, and the total count.
     */
    @JsonClass(generateAdapter = true)
    internal data class DomainCount(
        /** The total count of network calls for the given domain. */
        val requestCount: Int,
        /** The configured request capture limit for the given domain. */
        val captureLimit: Int
    )
}
