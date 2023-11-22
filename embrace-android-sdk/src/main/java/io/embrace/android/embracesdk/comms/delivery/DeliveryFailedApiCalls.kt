package io.embrace.android.embracesdk.comms.delivery

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.comms.api.ApiRequest
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A queue containing failed API calls.
 */
internal class DeliveryFailedApiCalls : ConcurrentLinkedQueue<DeliveryFailedApiCall>()

/**
 * A failed API call.
 */
internal data class DeliveryFailedApiCall(
    @SerializedName("apiRequest") val apiRequest: ApiRequest,
    @SerializedName("cachedPayload") val cachedPayloadFilename: String,
    @SerializedName("queueTime") val queueTime: Long? = null
)
