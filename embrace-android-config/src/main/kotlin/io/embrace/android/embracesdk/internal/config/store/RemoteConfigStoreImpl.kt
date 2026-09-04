package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.internal.config.cache.CachedConfiguration
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse
import io.embrace.android.embracesdk.internal.serialization.EmbraceBinary
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import kotlinx.serialization.builtins.nullable
import java.io.File

internal class RemoteConfigStoreImpl(
    private val serializer: PlatformSerializer,
    private val storageDir: File,
    private val deviceIdProvider: () -> String,
) : RemoteConfigStore {

    private val configFile by lazy { File(storageDir, "most_recent_response") }
    private val etagFile by lazy { File(storageDir, "etag") }

    // binary fast-path cache. Not created up-front: its absence is a clean cache miss that falls
    // back to [configFile]/[etagFile].
    private val cachedConfigFile by lazy { File(storageDir, "cached_config") }

    override fun loadResponse(): StoredConfigResponse? = loadFromCache() ?: loadFromJson()

    private fun loadFromCache(): StoredConfigResponse? {
        return try {
            val cached = cachedConfigFile.inputStream().buffered().use {
                EmbraceBinary.decodeFromStream(CachedConfiguration.serializer(), it)
            }
            StoredConfigResponse(
                cfg = cached.remoteConfig,
                etag = cached.etag,
                deviceId = cached.deviceId,
            )
        } catch (_: IllegalArgumentException) {
            // delete the cache file if it appears to be corrupted
            deleteQuietly(cachedConfigFile)
            null
        } catch (_: Throwable) {
            // assume error may be recoverable (e.g. transient IO)
            null
        }
    }

    private fun loadFromJson(): StoredConfigResponse? {
        return try {
            val cfg = configFile.inputStream().buffered().use {
                serializer.fromJson(it, RemoteConfig.serializer())
            }
            StoredConfigResponse(
                cfg = cfg,
                etag = readEtag(),
                deviceId = null,
            )
        } catch (_: IllegalArgumentException) {
            // delete the cache file if it appears to be corrupted
            deleteQuietly(configFile)
            null
        } catch (_: Throwable) {
            // assume error may be recoverable (e.g. transient IO)
            null
        }
    }

    private fun readEtag(): String? = runCatching { etagFile.readText() }.getOrNull()?.ifEmpty { null }

    override fun saveResponse(response: ConfigHttpResponse) {
        try {
            storageDir.mkdirs()
            configFile.outputStream().buffered().use { stream ->
                serializer.toJson(response.cfg, RemoteConfig.serializer().nullable, stream)
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
                deleteQuietly(cachedConfigFile)
                return
            }
            val cached = CachedConfiguration(
                deviceId = deviceIdProvider(),
                etag = response.etag,
                remoteConfig = cfg,
            )
            cachedConfigFile.outputStream().buffered().use { stream ->
                EmbraceBinary.encodeToStream(CachedConfiguration.serializer(), cached, stream)
            }
        } catch (exc: Exception) {
            // a partially-written or failed blob must not be read back: delete it and rely on the
            // fallback path that was already written successfully.
            deleteQuietly(cachedConfigFile)
        }
    }

    private fun purgeCache() {
        deleteQuietly(configFile)
        deleteQuietly(etagFile)
        deleteQuietly(cachedConfigFile)
    }

    private fun deleteQuietly(file: File) {
        runCatching { file.delete() }
    }
}
