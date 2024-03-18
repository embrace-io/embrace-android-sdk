package io.embrace.android.embracesdk.capture.envelope.log

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload

internal fun interface LogEnvelopeSource {
    fun getEnvelope(): Envelope<LogPayload>
}
