package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NetworkSessionV2(
    /** The list of network requests captured as part of the session. */
    @Json(name = "r") val requests: List<NetworkCallV2>,
    /** Counts of network requests per domain, only for domains exceeding the capture limit. */
    @Json(name = "c") val requestCounts: Map<String, DomainCount>
)
