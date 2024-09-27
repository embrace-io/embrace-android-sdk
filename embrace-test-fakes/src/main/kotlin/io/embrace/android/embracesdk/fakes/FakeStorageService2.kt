package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.storage.StorageService2
import java.io.InputStream

class FakeStorageService2(
    payloads: List<StoredTelemetryMetadata> = emptyList()
) : StorageService2 {
    private val serializer = EmbraceSerializer()

    val cachedPayloads = LinkedHashSet<StoredTelemetryMetadata>().apply {
        addAll(payloads)
    }

    override fun getPayloadsByPriority(): List<StoredTelemetryMetadata> = cachedPayloads.toList()

    override fun loadPayloadAsStream(payloadMetadata: StoredTelemetryMetadata): InputStream? {
        return if (cachedPayloads.contains(payloadMetadata)) {
            when (payloadMetadata.envelopeType.serializedType) {
                Envelope.sessionEnvelopeType ->
                    serializer.toJson(
                        Envelope(data = SessionPayload()),
                        Envelope.sessionEnvelopeType
                    ).byteInputStream()
                Envelope.logEnvelopeType ->
                    serializer.toJson(
                        Envelope(data = LogPayload()),
                        Envelope.logEnvelopeType
                    ).byteInputStream()
                else -> null
            }
        } else {
            null
        }
    }

    override fun deletePayload(payloadMetadata: StoredTelemetryMetadata) {
        cachedPayloads.remove(payloadMetadata)
    }
}
