package io.embrace.android.embracesdk.internal.capture.envelope.log

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload

internal interface LogEnvelopeSource {
    fun getBatchedLogEnvelope(): Envelope<LogPayload>

    fun getNonbatchedEnvelope(): List<Envelope<LogPayload>>
}
