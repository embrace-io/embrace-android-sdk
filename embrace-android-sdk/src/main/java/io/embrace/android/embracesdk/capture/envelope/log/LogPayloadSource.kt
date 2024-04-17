package io.embrace.android.embracesdk.capture.envelope.log

import io.embrace.android.embracesdk.internal.payload.LogPayload

internal interface LogPayloadSource {

    /**
     * Returns a [LogPayload] containing the next batch of objects to be sent
     */
    fun getBatchedLogPayload(): LogPayload

    /**
     * Returns a list of [LogPayload] that each contain a single high priority log
     */
    fun getNonbatchedLogPayloads(): List<LogPayload>
}
