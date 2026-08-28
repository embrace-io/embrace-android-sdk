package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType

/**
 * Version of the envelope schema used for session part payloads.
 */
internal const val SESSION_ENVELOPE_VERSION: String = "0.1.0"

/**
 * Type of the envelope used for session part payloads.
 */
internal const val SESSION_ENVELOPE_TYPE: String = "spans"

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
            SESSION_ENVELOPE_VERSION,
            SESSION_ENVELOPE_TYPE,
            payloadSource.getSessionPartPayload(endType, startNewSession, crashId),
        )
    }
}
