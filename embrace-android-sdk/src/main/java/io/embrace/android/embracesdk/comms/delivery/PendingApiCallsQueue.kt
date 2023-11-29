package io.embrace.android.embracesdk.comms.delivery

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.comms.api.ApiRequest
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A queue containing pending API calls.
 */
internal class PendingApiCallsQueue : ConcurrentLinkedQueue<PendingApiCall>()

/**
 * A pending API call.
 */
internal data class PendingApiCall(
    @SerializedName("apiRequest") val apiRequest: ApiRequest,
    @SerializedName("cachedPayload") val cachedPayloadFilename: String,
    @SerializedName("queueTime") val queueTime: Long? = null
)
