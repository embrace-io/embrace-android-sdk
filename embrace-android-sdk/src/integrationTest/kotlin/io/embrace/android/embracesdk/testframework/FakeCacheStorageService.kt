package io.embrace.android.embracesdk.testframework

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storedTelemetryComparator
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal class FakeCacheStorageService : PayloadStorageService {

    val storedPayloads = ConcurrentHashMap<StoredTelemetryMetadata, SerializationAction>()
    val deletedPayloads = CopyOnWriteArrayList<StoredTelemetryMetadata>()

    override fun store(metadata: StoredTelemetryMetadata, action: SerializationAction) {
        storedPayloads[metadata] = action
    }

    override fun delete(metadata: StoredTelemetryMetadata, callback: () -> Unit) {
        deletedPayloads.add(metadata)
        callback()
    }

    override fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream? {
        val action = storedPayloads[metadata] ?: return null
        val baos = ByteArrayOutputStream()
        action(baos)
        return ByteArrayInputStream(baos.toByteArray())
    }

    override fun getPayloadsByPriority(): List<StoredTelemetryMetadata> {
        return storedPayloads.keys.sortedWith(storedTelemetryComparator)
    }

    override fun getUndeliveredPayloads(): List<StoredTelemetryMetadata> {
        return emptyList()
    }
}
