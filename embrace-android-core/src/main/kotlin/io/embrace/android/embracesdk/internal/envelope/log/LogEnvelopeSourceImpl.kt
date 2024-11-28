package io.embrace.android.embracesdk.internal.envelope.log

import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.logs.LogRequest
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload

internal class LogEnvelopeSourceImpl(
    private val metadataSource: EnvelopeMetadataSource,
    private val resourceSource: EnvelopeResourceSource,
    private val logPayloadSource: LogPayloadSource,
) : LogEnvelopeSource {

    override fun getBatchedLogEnvelope(): Envelope<LogPayload> = getLogEnvelope(logPayloadSource.getBatchedLogPayload())

    override fun getSingleLogEnvelopes(): List<LogRequest<Envelope<LogPayload>>> {
        val payloads = logPayloadSource.getSingleLogPayloads()
        return if (payloads.isNotEmpty()) {
            payloads.map { LogRequest(payload = getLogEnvelope(it.payload), defer = it.defer) }
        } else {
            emptyList()
        }
    }

    override fun getEmptySingleLogEnvelope(): Envelope<LogPayload> {
        return getLogEnvelope(LogPayload(logs = emptyList()))
    }

    private fun getLogEnvelope(payload: LogPayload) = Envelope(
        resourceSource.getEnvelopeResource(),
        metadataSource.getEnvelopeMetadata(),
        "0.1.0",
        "logs",
        payload
    )
}
