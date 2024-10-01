package io.embrace.android.embracesdk.internal.delivery.execution

import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import java.io.InputStream

class NoopRequestExecutionService : RequestExecutionService {
    override fun attemptHttpRequest(
        payloadStream: () -> InputStream,
        envelopeType: SupportedEnvelopeType
    ): ApiResponse {
        return ApiResponse.None
    }
}
