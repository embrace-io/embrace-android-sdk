package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.session.SessionPartPayloadSource
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType

class FakeSessionPartEnvelopeSource(
    private val metadataSource: EnvelopeMetadataSource = FakeEnvelopeMetadataSource(),
    private val resourceSource: EnvelopeResourceSource = FakeEnvelopeResourceSource(),
    private val partPayloadSource: SessionPartPayloadSource = FakeSessionPartPayloadSource(),
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
            partPayloadSource.getSessionPartPayload(endType, startNewSession, crashId)
        )
    }

    override fun endSessionPart(
        endType: SessionPartSnapshotType,
        startNewSession: Boolean,
        crashId: String?,
    ) {
        partPayloadSource.endSessionPart(endType, startNewSession, crashId)
    }
}
