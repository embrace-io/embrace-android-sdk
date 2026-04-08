package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType

internal class SessionPartEnvelopeSourceImpl(
    private val metadataSource: EnvelopeMetadataSource,
    private val resourceSource: EnvelopeResourceSource,
    private val payloadSource: SessionPartPayloadSource,
) : SessionPartEnvelopeSource {

    override fun getEnvelope(
        endType: SessionPartSnapshotType,
        startNewSession: Boolean,
        crashId: String?,
    ): Envelope<SessionPartPayload> {
        return Envelope(
            resourceSource.getEnvelopeResource(),
            metadataSource.getEnvelopeMetadata(),
            "0.1.0",
            "spans",
            payloadSource.getSessionPartPayload(endType, startNewSession, crashId)
        )
    }
}
