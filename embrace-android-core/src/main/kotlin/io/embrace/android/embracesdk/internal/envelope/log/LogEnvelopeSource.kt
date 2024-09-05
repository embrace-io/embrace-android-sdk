package io.embrace.android.embracesdk.internal.envelope.log

import io.embrace.android.embracesdk.internal.logs.LogRequest
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload

public interface LogEnvelopeSource {
    public fun getBatchedLogEnvelope(): Envelope<LogPayload>

    public fun getSingleLogEnvelopes(): List<LogRequest<Envelope<LogPayload>>>
}
