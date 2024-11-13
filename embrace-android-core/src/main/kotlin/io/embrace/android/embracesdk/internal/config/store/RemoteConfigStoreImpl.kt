package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal class RemoteConfigStoreImpl(
    private val serializer: PlatformSerializer,
    storageDir: File,
) : RemoteConfigStore {

    private val configFile = File(storageDir, "most_recent_response").apply {
        createNewFile()
    }

    private val etagFile = File(storageDir, "etag").apply {
        createNewFile()
    }

    override fun loadConfig(): RemoteConfig? {
        try {
            GZIPInputStream(configFile.inputStream().buffered()).use {
                return serializer.fromJson(it, RemoteConfig::class.java)
            }
        } catch (exc: Exception) {
            return null
        }
    }

    override fun saveConfig(config: RemoteConfig) {
        try {
            GZIPOutputStream(configFile.outputStream().buffered()).use { stream ->
                serializer.toJson(config, RemoteConfig::class.java, stream)
            }
        } catch (ignored: Exception) {
        }
    }

    override fun retrieveEtag(): String? {
        return try {
            etagFile.readText().ifEmpty {
                null
            }
        } catch (exc: Exception) {
            null
        }
    }

    override fun storeEtag(etag: String) {
        try {
            etagFile.writeText(etag)
        } catch (ignored: Exception) {
        }
    }
}
