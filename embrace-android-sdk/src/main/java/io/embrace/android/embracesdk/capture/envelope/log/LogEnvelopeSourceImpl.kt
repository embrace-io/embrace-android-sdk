package io.embrace.android.embracesdk.capture.envelope.log

import io.embrace.android.embracesdk.capture.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.capture.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload

internal class LogEnvelopeSourceImpl(
    private val metadataSource: EnvelopeMetadataSource,
    private val resourceSource: EnvelopeResourceSource,
    private val logPayloadSource: LogPayloadSource,
) : LogEnvelopeSource {

    override fun getBatchedLogEnvelope() = getLogEnvelope(logPayloadSource.getBatchedLogPayload())

    override fun getNonbatchedEnvelope(): List<Envelope<LogPayload>> {
        val payloads = logPayloadSource.getNonbatchedLogPayloads()
        return if (payloads.isNotEmpty()) {
            payloads.map { getLogEnvelope(it) }
        } else {
            emptyList()
        }
    }

    private fun getLogEnvelope(payload: LogPayload) = Envelope(
        resourceSource.getEnvelopeResource(),
        metadataSource.getEnvelopeMetadata(),
        "0.1.0",
        "logs",
        payload
    )
}
