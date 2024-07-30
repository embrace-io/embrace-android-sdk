package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

public class SessionEnvelopeSourceImpl(
    private val metadataSource: EnvelopeMetadataSource,
    private val resourceSource: EnvelopeResourceSource,
    private val sessionPayloadSource: SessionPayloadSource,
) : SessionEnvelopeSource {

    override fun getEnvelope(endType: SessionSnapshotType, startNewSession: Boolean, crashId: String?): Envelope<SessionPayload> {
        return Envelope(
            resourceSource.getEnvelopeResource(),
            metadataSource.getEnvelopeMetadata(),
            "0.1.0",
            "spans",
            sessionPayloadSource.getSessionPayload(endType, startNewSession, crashId)
        )
    }
}
