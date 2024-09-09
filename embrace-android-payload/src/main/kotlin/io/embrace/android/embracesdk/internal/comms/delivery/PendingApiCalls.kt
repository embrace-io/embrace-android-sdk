package io.embrace.android.embracesdk.internal.comms.delivery

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.internal.comms.api.Endpoint

/**
 * A map containing a queue of pending API calls for each endpoint.
 */
@JsonClass(generateAdapter = true)
class PendingApiCalls(
    @Json(name = "pendingApiCallsMap") val pendingApiCallsMap: Map<Endpoint, MutableList<PendingApiCall>> = emptyMap()
)
