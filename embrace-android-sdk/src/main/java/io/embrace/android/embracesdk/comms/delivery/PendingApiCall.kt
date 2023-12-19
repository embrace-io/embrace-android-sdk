package io.embrace.android.embracesdk.comms.delivery

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.comms.api.ApiRequest

/**
 * A pending API call.
 */
internal data class PendingApiCall(
    @SerializedName("apiRequest") val apiRequest: ApiRequest,
    @SerializedName("cachedPayload") val cachedPayloadFilename: String,
    @SerializedName("queueTime") val queueTime: Long? = null
)
