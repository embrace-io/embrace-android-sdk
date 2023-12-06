package io.embrace.android.embracesdk.comms.delivery

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.comms.api.ApiRequest
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A queue containing pending API calls.
 */
internal class PendingApiCallsQueue : ConcurrentLinkedQueue<PendingApiCall>()

/**
 * A pending API call.
 */
@JsonClass(generateAdapter = true)
internal data class PendingApiCall(
    @Json(name = "apiRequest") val apiRequest: ApiRequest,
    @Json(name = "cachedPayload") val cachedPayloadFilename: String,
    @Json(name = "queueTime") val queueTime: Long? = null
)
