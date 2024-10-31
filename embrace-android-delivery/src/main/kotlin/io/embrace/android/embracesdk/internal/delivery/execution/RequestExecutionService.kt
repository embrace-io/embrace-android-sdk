package io.embrace.android.embracesdk.internal.delivery.execution

import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import java.io.InputStream

/**
 * This service executes HTTP requests & returns a result to the caller. It is not responsible for
 * scheduling, file cleanup, etc.
 */
interface RequestExecutionService {

    /**
     * Takes an [InputStream] of a payload and attempts an HTTP request to the Embrace backend.
     */
    fun attemptHttpRequest(
        payloadStream: () -> InputStream,
        envelopeType: SupportedEnvelopeType,
        payloadType: String,
    ): ExecutionResult
}
