package io.embrace.android.embracesdk.internal.envelope.log

import io.embrace.android.embracesdk.internal.arch.attrs.embProcessIdentifier
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore.Companion.createNativeCrashEnvelopeMetadata
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSource
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.otel.logs.LogRequest
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Envelope.Companion.createLogEnvelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes

internal class LogEnvelopeSourceImpl(
    private val metadataSource: EnvelopeMetadataSource,
    private val resourceSource: EnvelopeResourceSource,
    private val logPayloadSource: LogPayloadSource,
    private val cachedLogEnvelopeStore: CachedLogEnvelopeStore?,
) : LogEnvelopeSource {

    override fun getBatchedLogEnvelope(): Envelope<LogPayload> = getLogEnvelope(logPayloadSource.getBatchedLogPayload())

    override fun getSingleLogEnvelopes(): List<LogRequest<Envelope<LogPayload>>> {
        val requests = logPayloadSource.getSingleLogPayloads()
        return if (requests.isNotEmpty()) {
            requests.map { LogRequest(payload = getLogEnvelope(it.payload), defer = it.defer) }
        } else {
            emptyList()
        }
    }

    override fun getEmptySingleLogEnvelope(): Envelope<LogPayload> {
        return getLogEnvelope(LogPayload(logs = emptyList()))
    }

    @OptIn(IncubatingApi::class)
    private fun getLogEnvelope(payload: LogPayload): Envelope<LogPayload> {
        if (cachedLogEnvelopeStore != null && payload.findType() == PayloadType.NATIVE_CRASH) {
            val nativeCrash = payload.logs?.firstOrNull()
            val envelope = cachedLogEnvelopeStore.get(
                createNativeCrashEnvelopeMetadata(
                    sessionId = nativeCrash?.attributes?.findAttributeValue(SessionAttributes.SESSION_ID),
                    processIdentifier = nativeCrash?.attributes?.findAttributeValue(embProcessIdentifier.name)
                )
            )

            if (envelope != null) {
                return envelope.copy(data = payload)
            }
        }

        return payload.createLogEnvelope(
            resource = resourceSource.getEnvelopeResource(),
            metadata = metadataSource.getEnvelopeMetadata()
        )
    }

    private fun LogPayload.findType(): PayloadType =
        PayloadType.fromValue(logs?.firstOrNull()?.attributes?.findAttributeValue("emb.type"))
}
