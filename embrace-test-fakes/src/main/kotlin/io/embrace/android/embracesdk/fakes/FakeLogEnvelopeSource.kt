package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.log.LogEnvelopeSource
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSource
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.logs.LogRequest
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Envelope.Companion.createLogEnvelope
import io.embrace.android.embracesdk.internal.payload.LogPayload

class FakeLogEnvelopeSource(
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

    override fun getEmptySingleLogEnvelope(): Envelope<LogPayload> = getLogEnvelope(LogPayload())

    private fun getLogEnvelope(payload: LogPayload) = payload.createLogEnvelope(
        resource = resourceSource.getEnvelopeResource(),
        metadata = metadataSource.getEnvelopeMetadata(),
    )
}
