package io.embrace.android.embracesdk.capture.envelope.log

import io.embrace.android.embracesdk.capture.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.capture.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.Envelope

internal class LogEnvelopeSourceImpl(
    private val metadataSource: EnvelopeMetadataSource,
    private val resourceSource: EnvelopeResourceSource,
    private val logPayloadSource: LogPayloadSource,
) : LogEnvelopeSource {

    override fun getEnvelope() = Envelope(
        resourceSource.getEnvelopeResource(),
        metadataSource.getEnvelopeMetadata(),
        null,
        null,
        logPayloadSource.getLogPayload()
    )
}
