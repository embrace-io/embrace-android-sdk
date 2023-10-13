package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.ApiRequest
import java.util.concurrent.ConcurrentLinkedQueue

internal class DeliveryFailedApiCalls : ConcurrentLinkedQueue<DeliveryFailedApiCall>()

internal data class DeliveryFailedApiCall(val apiRequest: ApiRequest, val cachedPayload: String)
