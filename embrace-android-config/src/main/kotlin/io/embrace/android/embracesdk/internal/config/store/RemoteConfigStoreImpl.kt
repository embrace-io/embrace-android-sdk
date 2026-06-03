package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehaviorImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

internal class RemoteConfigStoreImpl(
    private val serializer: PlatformSerializer,
    storageDir: File,
    private val deviceIdProvider: () -> String,
) : RemoteConfigStore {

    private val encoder = BinaryRemoteConfigEncoder()
    private val decoder = BinaryRemoteConfigDecoder()

    init {
        storageDir.mkdirs()
    }

    private val jsonFile = File(storageDir, "most_recent_response").apply {
        createNewFile()
    }

    private val binaryCacheFile = File(storageDir, "config_cache.bin")

    private val etagFile = File(storageDir, "etag").apply {
        createNewFile()
    }

    override fun loadResponse(): ConfigHttpResponse? {
        val cfg = readBinaryCache() ?: readJsonPrimary() ?: return null
        return ConfigHttpResponse(
            cfg,
            etagFile.readText().ifEmpty {
                null
            }
        )
    }

    override fun saveResponse(response: ConfigHttpResponse) {
        val cfg = response.cfg ?: return

        // invalidate the derived cache up-front so it can never be staler than the primary we're
        // about to write: a crash mid-save leaves the cache absent (→ JSON fallback) rather than
        // pointing at an older config. It is regenerated below once the primary is durable.
        binaryCacheFile.delete()

        try {
            // the JSON primary (and its etag) is the source of truth and must be persisted first
            jsonFile.outputStream().buffered().use { stream ->
                serializer.toJson(cfg, RemoteConfig::class.java, stream)
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

        // the binary cache is a best-effort accelerator derived from the primary; if it can't be
        // written, drop it so the next load falls back to the JSON primary
        try {
            DataOutputStream(binaryCacheFile.outputStream().buffered()).use { stream ->
                with(encoder) { stream.write(deviceIdProvider(), cfg) }
            }
        } catch (exc: Exception) {
            binaryCacheFile.delete()
        }
    }

    override fun loadDeviceId(): String? = try {
        DataInputStream(binaryCacheFile.inputStream().buffered()).use { input ->
            with(decoder) { input.readDeviceId() }
        }
    } catch (exc: Exception) {
        null
    }

    private fun readBinaryCache(): RemoteConfig? = try {
        DataInputStream(binaryCacheFile.inputStream().buffered()).use { input ->
            with(decoder) { input.readRemoteConfig(::isSdkDisabled) }
        }
    } catch (exc: Exception) {
        null
    }

    private fun readJsonPrimary(): RemoteConfig? = try {
        jsonFile.inputStream().buffered().use {
            serializer.fromJson(it, RemoteConfig::class.java)
        }
    } catch (exc: Exception) {
        null
    }

    private fun isSdkDisabled(header: BinaryRemoteConfigDecoder.Header): Boolean =
        SdkModeBehaviorImpl(
            BehaviorThresholdCheck { header.deviceId },
            RemoteConfig(threshold = header.threshold),
        ).isSdkDisabled()

    private fun purgeCache() {
        try {
            jsonFile.delete()
            binaryCacheFile.delete()
            etagFile.delete()
        } catch (ignored: Exception) {
        }
    }
}
