package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.storage.StorageService2
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class FakeStorageService2 : StorageService2 {
    private val serializer = TestPlatformSerializer()
    private val cachedPayloads = LinkedHashMap<StoredTelemetryMetadata, ByteArray>()

    var failStorage: Boolean = false

    override fun store(metadata: StoredTelemetryMetadata, action: SerializationAction) {
        if (failStorage) {
            throw IOException("Failed to store payload")
        }

        val baos = ByteArrayOutputStream()
        action(baos)
        cachedPayloads[metadata] = baos.toByteArray()
    }

    override fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream? {
        return try {
            cachedPayloads[metadata]?.let { bytes ->
                ByteArrayInputStream(bytes)
            }
        } catch (t: Throwable) {
            null
        }
    }

    override fun delete(metadata: StoredTelemetryMetadata) {
        cachedPayloads.remove(metadata)
    }

    override fun getPayloadsByPriority(): List<StoredTelemetryMetadata> = cachedPayloads.keys.toList()

    fun addFakePayload(metadata: StoredTelemetryMetadata) {
        store(metadata) { stream ->
            serializer.toJson(createFakePayload(metadata), metadata.envelopeType.serializedType, stream)
        }
    }

    fun storedPayloadCount() = cachedPayloads.size

    fun clearStorage() = cachedPayloads.clear()

    private fun createFakePayload(metadata: StoredTelemetryMetadata) =
        when (metadata.envelopeType.serializedType) {
            Envelope.sessionEnvelopeType -> Envelope(data = SessionPayload())
            Envelope.logEnvelopeType -> Envelope(data = LogPayload())
            else -> null
        }
}
