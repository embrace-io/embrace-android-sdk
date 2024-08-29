package io.embrace.android.embracesdk.internal.comms.delivery

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.internal.comms.api.ApiRequest

/**
 * A pending API call.
 */
@JsonClass(generateAdapter = true)
public data class PendingApiCall(
    @Json(name = "apiRequest") val apiRequest: ApiRequest,
    @Json(name = "cachedPayload") val cachedPayloadFilename: String,
    @Json(name = "queueTime") val queueTime: Long? = null
)
