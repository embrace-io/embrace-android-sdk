package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.FakeRemoteConfigSource
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.source.ConfigHttpResponse
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStoreImpl
import io.embrace.android.embracesdk.internal.payload.AppFramework
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

internal class ConfigServiceImplTest {

    private val serializer = TestPlatformSerializer()
    private lateinit var service: ConfigServiceImpl
    private lateinit var remoteConfig: RemoteConfig

    /**
     * Setup before each test.
     */
    @Before
    fun setup() {
        remoteConfig = RemoteConfig()
        service = createService()
        assertFalse(service.isOnlyUsingOtelExporters())
    }

    @Test
    fun `test app framework`() {
        assertEquals(AppFramework.NATIVE, service.appFramework)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEmptyAppId() {
        createService(appId = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNullAppId() {
        createService(appId = null)
    }

    @Test
    fun testNoAppIdRequiredWithExporters() {
        val service = createService(hasConfiguredExporters = { true }, appId = null)
        assertNotNull(service)
        assertTrue(service.isOnlyUsingOtelExporters())
    }

    @Test
    fun `no pct rollouts reported without remote config`() {
        assertEquals(emptyList<String>(), service.enabledPctRollouts)
    }

    @Test
    fun `kotlin sdk rollout reported only when the device runs it under a partial rollout`() {
        mapOf(1f to false, 50f to true, 100f to false).forEach { (pct, expected) ->
            val service = createService(
                persisted = RemoteConfig(otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = pct)),
            )
            assertEquals("pct=$pct", expected, "okt" in service.enabledPctRollouts)
        }
    }

    /**
     * Create a new instance of the [ConfigServiceImpl]
     */
    private fun createService(
        hasConfiguredExporters: () -> Boolean = { false },
        appId: String? = "AbCdE",
        persisted: RemoteConfig? = null,
    ): ConfigServiceImpl {
        val instrumentedConfig = FakeInstrumentedConfig(project = FakeProjectConfig(appId = appId))
        val filesDir = Files.createTempDirectory("tmp").toFile()
        if (persisted != null) {
            RemoteConfigStoreImpl(
                serializer = serializer,
                storageDir = File(filesDir, PersistedConfig.STORAGE_DIR_NAME),
                deviceIdProvider = { DEVICE_ID },
                deliveredAtProvider = { 1L },
            ).saveResponse(ConfigHttpResponse(persisted, "etag"))
        }
        return ConfigServiceImpl(
            instrumentedConfig = instrumentedConfig,
            persistedConfig = PersistedConfig(
                serializer = serializer,
                filesDir = filesDir,
                instrumentedConfig = instrumentedConfig,
                keyValueStore = lazyOf(FakeKeyValueStore()),
                uuidSource = TestUuidSource(),
                clock = FakeClock(),
            ),
            worker = fakeBackgroundWorker(),
            serializer = serializer,
            remoteConfigSource = FakeRemoteConfigSource(),
            primaryAbi = "arm64-v8a",
            logger = FakeInternalLogger(),
            hasConfiguredOtlpExport = hasConfiguredExporters,
        )
    }

    private companion object {
        const val DEVICE_ID = "07D85B44E4E245F4A30E559BFC0D0739"
    }
}
