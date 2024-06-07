package io.embrace.android.embracesdk.capture.envelope.session

import io.embrace.android.embracesdk.capture.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.capture.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal class SessionEnvelopeSourceImpl(
    private val metadataSource: EnvelopeMetadataSource,
    private val resourceSource: EnvelopeResourceSource,
    private val sessionPayloadSource: SessionPayloadSource,
) : SessionEnvelopeSource {

    override fun getEnvelope(endType: SessionSnapshotType, crashId: String?): Envelope<SessionPayload> {
        return Envelope(
            resourceSource.getEnvelopeResource(),
            metadataSource.getEnvelopeMetadata(),
            "0.1.0",
            "session",
            sessionPayloadSource.getSessionPayload(endType, crashId)
        )
    }
}
