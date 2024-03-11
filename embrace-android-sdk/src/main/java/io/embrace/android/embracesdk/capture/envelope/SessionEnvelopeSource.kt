package io.embrace.android.embracesdk.capture.envelope

import io.embrace.android.embracesdk.capture.envelope.session.SessionPayloadSource
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal class SessionEnvelopeSource(
    private val sessionPayloadSource: SessionPayloadSource
) : EnvelopeSource<SessionPayload> {

    override fun getEnvelope(endType: SessionSnapshotType): Envelope<SessionPayload> {
        sessionPayloadSource.getSessionPayload(endType)
        throw NotImplementedError("Not yet implemented")
    }
}
