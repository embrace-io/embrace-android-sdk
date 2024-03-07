package io.embrace.android.embracesdk.capture.envelope

import io.embrace.android.embracesdk.capture.envelope.session.SessionPayloadSource
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload

internal class SessionEnvelopeSource(
    private val sessionPayloadSource: SessionPayloadSource
) : EnvelopeSource<SessionPayload> {

    override fun getEnvelope(): Envelope<SessionPayload> {
        sessionPayloadSource.getSessionPayload()
        throw NotImplementedError("Not yet implemented")
    }
}
