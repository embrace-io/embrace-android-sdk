package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

class FakePayloadStorageService(
    var currentProcessId: String = "pid"
) : PayloadStorageService {
    private val serializer = TestPlatformSerializer()
    private val cachedPayloads = LinkedHashMap<StoredTelemetryMetadata, ByteArray>()

    val storeCount = AtomicInteger(0)
    var failStorage: Boolean = false

    override fun store(metadata: StoredTelemetryMetadata, action: SerializationAction) {
        storeCount.incrementAndGet()
        if (failStorage) {
            throw IOException("Failed to store payload")
        }

        val baos = ByteArrayOutputStream()
        action(GZIPOutputStream(baos))
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

    override fun getPayloadsByPriority(): List<StoredTelemetryMetadata> =
        cachedPayloads.filter { it.key.complete }.keys.toList()

    override fun getUndeliveredPayloads(): List<StoredTelemetryMetadata> =
        cachedPayloads.filterNot { it.key.complete && it.key.processId != currentProcessId }.keys.toList()

    fun <T> addPayload(metadata: StoredTelemetryMetadata, data: T) {
        store(metadata) { stream ->
            serializer.toJson(data, metadata.envelopeType.serializedType, stream)
        }
    }

    fun addFakePayload(metadata: StoredTelemetryMetadata) = addPayload(metadata, createFakePayload(metadata))

    fun storedFilenames(): List<String> = cachedPayloads.keys.map { it.filename }

    fun storedPayloads(): List<ByteArray> = cachedPayloads.values.toList()

    fun storedPayloadCount() = cachedPayloads.size

    fun clearStorage() {
        cachedPayloads.clear()
    }

    private fun createFakePayload(metadata: StoredTelemetryMetadata) =
        when (metadata.envelopeType.serializedType) {
            Envelope.sessionEnvelopeType -> Envelope(data = SessionPayload())
            Envelope.logEnvelopeType -> Envelope(data = LogPayload())
            else -> null
        }
}
