package io.embrace.android.embracesdk.internal.envelope.log

import io.embrace.android.embracesdk.internal.logs.LogRequest
import io.embrace.android.embracesdk.internal.payload.LogPayload

interface LogPayloadSource {

    /**
     * Returns a [LogPayload] containing the next batch of objects to be sent
     */
    fun getBatchedLogPayload(): LogPayload

    /**
     * Returns a list of [LogPayload] that each contain a single high priority log
     */
    fun getSingleLogPayloads(): List<LogRequest<LogPayload>>
}
