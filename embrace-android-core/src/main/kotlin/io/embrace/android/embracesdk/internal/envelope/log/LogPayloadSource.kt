package io.embrace.android.embracesdk.internal.envelope.log

import io.embrace.android.embracesdk.internal.logs.LogRequest
import io.embrace.android.embracesdk.internal.payload.LogPayload

public interface LogPayloadSource {

    /**
     * Returns a [LogPayload] containing the next batch of objects to be sent
     */
    public fun getBatchedLogPayload(): LogPayload

    /**
     * Returns a list of [LogPayload] that each contain a single high priority log
     */
    public fun getNonbatchedLogPayloads(): List<LogRequest<LogPayload>>
}
