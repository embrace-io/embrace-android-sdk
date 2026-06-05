package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.internal.config.cache.CachedConfiguration
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse
import io.embrace.android.embracesdk.internal.serialization.EmbraceBinary
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.serialization.decodeFromStream
import io.embrace.android.embracesdk.internal.serialization.encodeToStream
import io.embrace.android.embracesdk.internal.serialization.fromJson
import io.embrace.android.embracesdk.internal.serialization.toJson
import java.io.File

internal class RemoteConfigStoreImpl(
    private val serializer: PlatformSerializer,
    storageDir: File,
    private val deviceIdProvider: () -> String,
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

    // binary fast-path cache. Not created up-front: its absence is a clean cache miss that falls
    // back to [configFile]/[etagFile].
    private val cachedConfigFile = File(storageDir, "cached_config")

    override fun loadResponse(): StoredConfigResponse? = loadFromCache() ?: loadFromJson()

    private fun loadFromCache(): StoredConfigResponse? {
        return try {
            val cached = cachedConfigFile.inputStream().buffered().use {
                EmbraceBinary.decodeFromStream<CachedConfiguration>(it)
            }
            StoredConfigResponse(
                cfg = cached.remoteConfig,
                etag = cached.etag,
                deviceId = cached.deviceId,
            )
        } catch (exc: Exception) {
            null
        }
    }

    private fun loadFromJson(): StoredConfigResponse? {
        return try {
            val cfg = configFile.inputStream().buffered().use {
                serializer.fromJson<RemoteConfig>(it)
            }
            StoredConfigResponse(
                cfg = cfg,
                etag = etagFile.readText().ifEmpty {
                    null
                },
                deviceId = null,
            )
        } catch (exc: Exception) {
            null
        }
    }

    override fun saveResponse(response: ConfigHttpResponse) {
        try {
            configFile.outputStream().buffered().use { stream ->
                serializer.toJson<RemoteConfig?>(response.cfg, stream)
            }
            response.etag?.let(etagFile::writeText)
        } catch (exc: Exception) {
            // paranoia: purge the cache
            // to avoid the possibility of getting trapped with stale config
            // where the SDK is disabled & persistence fails. In that scenario we prefer
            // the default SDK behavior which will fetch the correct config eventually
            purgeCache()
            return
        }

        // only write the binary fast-path cache once the canonical files are safely persisted, so
        // it can never disagree with the fallback path.
        saveCache(response)
    }

    private fun saveCache(response: ConfigHttpResponse) {
        try {
            // nothing to cache without a config; remove any stale blob so the fast path is skipped.
            val cfg = response.cfg ?: run {
                cachedConfigFile.delete()
                return
            }
            val cached = CachedConfiguration(
                deviceId = deviceIdProvider(),
                etag = response.etag,
                remoteConfig = cfg,
            )
            cachedConfigFile.outputStream().buffered().use { stream ->
                EmbraceBinary.encodeToStream(cached, stream)
            }
        } catch (exc: Exception) {
            // a partially-written or failed blob must not be read back: delete it and rely on the
            // fallback path that was already written successfully.
            runCatching { cachedConfigFile.delete() }
        }
    }

    private fun purgeCache() {
        try {
            configFile.delete()
            etagFile.delete()
            cachedConfigFile.delete()
        } catch (ignored: Exception) {
        }
    }
}
