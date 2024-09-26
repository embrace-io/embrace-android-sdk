package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.injection.SerializationAction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class FakePayloadStorageService : PayloadStorageService {

    private val serializer = TestPlatformSerializer()

    val storedFilenames: MutableList<String> = mutableListOf()
    val storedObjects: MutableList<Any> = mutableListOf()
    var failStorage: Boolean = false

    override fun store(metadata: StoredTelemetryMetadata, action: SerializationAction) {
        if (failStorage) {
            throw IOException("Failed to store payload")
        }
        storedFilenames.add(metadata.filename)
        val baos = ByteArrayOutputStream()
        action(baos)
        val bytes = baos.toByteArray()
        val inputStream = ByteArrayInputStream(bytes)
        storedObjects.add(serializer.fromJson(inputStream, Map::class.java))
    }

    override fun delete(metadata: StoredTelemetryMetadata) {
    }

    override fun loadPayloadAsStream(metadata: StoredTelemetryMetadata): InputStream? {
        return null
    }
}
