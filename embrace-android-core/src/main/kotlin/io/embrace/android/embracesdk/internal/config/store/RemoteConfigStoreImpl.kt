package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import java.io.File

internal class RemoteConfigStoreImpl(
    private val serializer: PlatformSerializer,
    storageDir: File,
) : RemoteConfigStore {

    init {
        storageDir.mkdirs()
    }

    private val configFile = File(storageDir, "most_recent_response").apply {
        createNewFile()
    }

    private val etagFile = File(storageDir, "etag").apply {
        createNewFile()
    }

    override fun loadResponse(): ConfigHttpResponse? {
        try {
            val cfg = configFile.inputStream().buffered().use {
                serializer.fromJson(it, RemoteConfig::class.java)
            }
            return ConfigHttpResponse(
                cfg,
                etagFile.readText().ifEmpty {
                    null
                }
            )
        } catch (exc: Exception) {
            return null
        }
    }

    override fun saveResponse(response: ConfigHttpResponse) {
        try {
            configFile.outputStream().buffered().use { stream ->
                serializer.toJson(response.cfg, RemoteConfig::class.java, stream)
            }
            response.etag?.let(etagFile::writeText)
        } catch (ignored: Exception) {
        }
    }
}
