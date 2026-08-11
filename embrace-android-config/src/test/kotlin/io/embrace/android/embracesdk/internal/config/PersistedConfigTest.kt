package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStoreImpl
import io.embrace.android.embracesdk.internal.serialization.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class PersistedConfigTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val serializer = TestPlatformSerializer()

    @Test
    fun `remote config is loaded from disk`() {
        val cfg = RemoteConfig(threshold = 92)
        val persistedConfig = createPersistedConfig(persisted = cfg)
        assertEquals(cfg, persistedConfig.remoteConfig)
    }

    @Test
    fun `remote config is null when nothing was persisted`() {
        assertNull(createPersistedConfig().remoteConfig)
    }

    @Test
    fun `kotlin sdk enabled by remote config`() {
        val persistedConfig = createPersistedConfig(
            persisted = RemoteConfig(otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 100f)),
        )
        assertTrue(persistedConfig.otelBehavior.shouldUseKotlinSdk())
    }

    @Test
    fun `kotlin sdk disabled by remote config overrides local flag`() {
        val persistedConfig = createPersistedConfig(
            persisted = RemoteConfig(otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 0f)),
            localEnabled = true,
        )
        assertFalse(persistedConfig.otelBehavior.shouldUseKotlinSdk())
    }

    @Test
    fun `kotlin sdk falls back to local flag when remote config absent`() {
        assertTrue(createPersistedConfig(localEnabled = true).otelBehavior.shouldUseKotlinSdk())
        assertFalse(createPersistedConfig(localEnabled = false).otelBehavior.shouldUseKotlinSdk())
    }

    @Test
    fun `device id sourced from key value store when not co-cached`() {
        val store = FakeKeyValueStore().apply {
            editAndCommit { putString("io.embrace.deviceid", "persisted-device-id") }
        }
        val persistedConfig = createPersistedConfig(store = lazyOf(store))
        assertEquals("persisted-device-id", persistedConfig.deviceId)
    }

    @Test
    fun `persisted config ignored entirely when there is no app id`() {
        val persistedConfig = createPersistedConfig(
            persisted = RemoteConfig(otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 100f)),
            appId = null,
        )
        assertTrue(persistedConfig.onlyOtelExportEnabled)
        assertNull(persistedConfig.store)
        assertNull(persistedConfig.remoteConfig)
        assertFalse(persistedConfig.otelBehavior.shouldUseKotlinSdk())
    }

    /**
     * [ConfigServiceImpl] decides whether to build an HTTP config source from
     * [PersistedConfig.onlyOtelExportEnabled] while the store here is built from the same flag. If the
     * two ever disagree the scheduled config request throws on `checkNotNull` and cancels itself, so
     * pin them to one another.
     */
    @Test
    fun `store presence is the inverse of onlyOtelExportEnabled`() {
        createPersistedConfig(appId = "abcde").run {
            assertFalse(onlyOtelExportEnabled)
            assertNotNull(store)
        }
        createPersistedConfig(appId = null).run {
            assertTrue(onlyOtelExportEnabled)
            assertNull(store)
        }
    }

    @Test
    fun `device id sourced from the binary cache`() {
        val persistedConfig = createPersistedConfig(
            persisted = RemoteConfig(threshold = 92),
            cachedDeviceId = "cached-device-id",
        )
        assertEquals("cached-device-id", persistedConfig.deviceId)
    }

    @Test
    fun `corrupt config on disk degrades to no config rather than throwing`() {
        val filesDir = temporaryFolder.newFolder()
        val storageDir = File(filesDir, PersistedConfig.STORAGE_DIR_NAME).apply { mkdirs() }
        File(storageDir, "most_recent_response").writeText("!!! not json !!!")
        File(storageDir, "cached_config").writeBytes(ByteArray(8))

        val persistedConfig = createPersistedConfig(filesDir = filesDir)

        assertNull(persistedConfig.response)
        assertNull(persistedConfig.remoteConfig)
        assertFalse(persistedConfig.otelBehavior.shouldUseKotlinSdk())
    }

    @Test
    fun `key value store is not touched when the config is never read`() {
        var resolved = false
        val store = lazy {
            resolved = true
            FakeKeyValueStore()
        }
        createPersistedConfig(store = store)
        assertFalse(resolved)
    }

    @Test
    fun `key value store is not touched when the binary cache supplies the device id`() {
        var resolved = false
        val store = lazy {
            resolved = true
            FakeKeyValueStore()
        }
        val persistedConfig = createPersistedConfig(
            persisted = RemoteConfig(threshold = 92),
            cachedDeviceId = "cached-device-id",
            store = store,
        )

        assertEquals("cached-device-id", persistedConfig.deviceId)
        assertFalse(resolved)
    }

    private fun createPersistedConfig(
        persisted: RemoteConfig? = null,
        localEnabled: Boolean = false,
        appId: String? = "abcde",
        store: Lazy<FakeKeyValueStore> = lazyOf(FakeKeyValueStore()),
        cachedDeviceId: String? = null,
        filesDir: File = temporaryFolder.newFolder(),
    ): PersistedConfig {
        if (persisted != null && cachedDeviceId != null) {
            RemoteConfigStoreImpl(
                serializer = serializer,
                storageDir = File(filesDir, PersistedConfig.STORAGE_DIR_NAME),
                deviceIdProvider = { cachedDeviceId },
            ).saveResponse(ConfigHttpResponse(persisted, "etag"))
        } else if (persisted != null) {
            val storageDir = File(filesDir, PersistedConfig.STORAGE_DIR_NAME).apply { mkdirs() }
            File(storageDir, "most_recent_response").outputStream().buffered().use {
                serializer.toJson(persisted, it)
            }
        }
        return PersistedConfig(
            serializer = serializer,
            filesDir = filesDir,
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(otelKotlinSdkEnabled = localEnabled),
                project = FakeProjectConfig(appId = appId),
            ),
            keyValueStore = store,
            uuidSource = TestUuidSource(),
        )
    }
}
