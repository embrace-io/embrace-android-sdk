package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import java.io.InputStream

class FakeRequestExecutionService : RequestExecutionService {

    override fun attemptHttpRequest(
        payloadStream: () -> InputStream,
        envelopeType: SupportedEnvelopeType
    ): ApiResponse {
        return ApiResponse.None
    }
}
