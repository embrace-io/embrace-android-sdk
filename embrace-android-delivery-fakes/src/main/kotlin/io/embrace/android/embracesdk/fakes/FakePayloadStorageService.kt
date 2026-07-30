package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.SerializationAction
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class FakePayloadStorageService(
    processIdentifier: String = UUID.randomUUID().toString(),
    workerExecutor: BlockableExecutorService? = null,
) : PayloadStorageService {
    private val processIdProvider: () -> String = { processIdentifier }
    private val serializer = TestPlatformSerializer()
    private val cachedPayloads = Collections.synchronizedMap(LinkedHashMap<StoredTelemetryMetadata, ByteArray>())

    private val worker = if (workerExecutor != null) {
        PriorityWorker<StoredTelemetryMetadata>(workerExecutor)
    } else {
        null
    }

    val storeCount = AtomicInteger(0)
    val deleteCount = AtomicInteger(0)

    /**
     * Number of streams returned by [loadPayloadAsStream] that have since been closed.
     * Lets tests verify that callers close the payload stream they were handed.
     */
    val closedStreamCount = AtomicInteger(0)
    var failStorage: Boolean = false

    override fun store(metadata: StoredTelemetryMetadata, action: SerializationAction) {
        storeCount.incrementAndGet()
        if (failStorage) {
            throw IOException("Failed to store payload")
        }

        val baos = ByteArrayOutputStream()
        if (metadata.payloadType != PayloadType.ATTACHMENT) {
            action(GZIPOutputStream(baos))
        } else {
            action(baos)
        }
        cachedPayloads[metadata] = baos.toByteArray()
    }

    override fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream? {
        return try {
            cachedPayloads[metadata]?.let { bytes ->
                object : FilterInputStream(ByteArrayInputStream(bytes)) {
                    override fun close() {
                        closedStreamCount.incrementAndGet()
                        super.close()
                    }
                }
            }
        } catch (t: Throwable) {
            null
        }
    }

    override fun delete(metadata: StoredTelemetryMetadata, callback: () -> Unit) {
        if (worker == null) {
            deleteSynchronous(metadata, callback)
        } else {
            worker.submit(metadata) {
                deleteSynchronous(metadata, callback)
            }
        }
    }

    override fun getPayloadsByPriority(): List<StoredTelemetryMetadata> =
        cachedPayloads.filter { it.key.complete }.keys.toList()

    override fun getUndeliveredPayloads(): List<StoredTelemetryMetadata> =
        cachedPayloads.filter { !it.key.complete && it.key.processIdentifier != processIdProvider() }.keys.toList()

    fun addPayload(metadata: StoredTelemetryMetadata, data: Envelope<*>) {
        store(metadata) { stream ->
            serializer.toJson(data, metadata.envelopeType.requireEnvelopeSerializer(), stream)
        }
    }

    fun addFakePayload(metadata: StoredTelemetryMetadata) {
        createFakePayload(metadata)?.let { addPayload(metadata, it) }
    }

    fun storedFilenames(): List<String> = cachedPayloads.keys.map { it.filename }

    fun storedPayloads(): List<ByteArray> = cachedPayloads.values.toList()

    fun storedPayloadMetadata(): List<StoredTelemetryMetadata> {
        return cachedPayloads.keys.toList()
    }

    fun storedPayloadCount() = cachedPayloads.size

    fun clearStorage() {
        cachedPayloads.clear()
    }

    private fun createFakePayload(metadata: StoredTelemetryMetadata) =
        when (metadata.envelopeType) {
            SupportedEnvelopeType.SESSION -> Envelope(data = SessionPartPayload())
            SupportedEnvelopeType.CRASH,
            SupportedEnvelopeType.LOG,
            SupportedEnvelopeType.BLOB,
            -> Envelope(data = LogPayload())
            SupportedEnvelopeType.ATTACHMENT -> null
        }

    private fun deleteSynchronous(metadata: StoredTelemetryMetadata, callback: () -> Unit) {
        cachedPayloads.remove(metadata)
        deleteCount.getAndIncrement()
        callback()
    }

    fun getPersistedCrashLog(
        payloadType: PayloadType = PayloadType.JVM_CRASH,
    ): Envelope<LogPayload> {
        val metadata = storedPayloadMetadata().single {
            it.payloadType == payloadType && it.complete
        }
        return serializer.fromJson(
            GZIPInputStream(loadPayloadAsStream(metadata)),
            Envelope.logEnvelopeSerializer,
        )
    }
}
