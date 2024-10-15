package io.embrace.android.embracesdk.internal.delivery.execution

import io.embrace.android.embracesdk.internal.comms.api.ApiRequestV2
import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import java.io.InputStream

interface EmbraceHttpClient {
    fun executeRequest(apiRequest: ApiRequestV2, payloadStream: () -> InputStream): ApiResponse
}
