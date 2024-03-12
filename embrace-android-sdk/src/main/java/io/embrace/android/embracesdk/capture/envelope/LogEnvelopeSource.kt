package io.embrace.android.embracesdk.capture.envelope

import io.embrace.android.embracesdk.capture.envelope.log.LogPayloadSource
import io.embrace.android.embracesdk.capture.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.capture.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal class LogEnvelopeSource(
    private val metadataSource: EnvelopeMetadataSource,
    private val resourceSource: EnvelopeResourceSource,
    private val logPayloadSource: LogPayloadSource,
) : EnvelopeSource<LogPayload> {

    override fun getEnvelope(endType: SessionSnapshotType) = Envelope(
        resourceSource.getEnvelopeResource(),
        metadataSource.getEnvelopeMetadata(),
        null,
        null,
        logPayloadSource.getLogPayload()
    )
}
