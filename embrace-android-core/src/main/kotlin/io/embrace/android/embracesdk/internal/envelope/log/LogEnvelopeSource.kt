package io.embrace.android.embracesdk.internal.envelope.log

import io.embrace.android.embracesdk.internal.logs.LogRequest
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload

interface LogEnvelopeSource {
    fun getBatchedLogEnvelope(): Envelope<LogPayload>

    fun getSingleLogEnvelopes(): List<LogRequest<Envelope<LogPayload>>>

    fun getEmptySingleLogEnvelope(): Envelope<LogPayload>
}
