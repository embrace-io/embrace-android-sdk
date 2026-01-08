package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.AppFramework
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

internal class ConfigServiceImplTest {

    private val okHttpClient = OkHttpClient()
    private val serializer = TestPlatformSerializer()
    private lateinit var service: ConfigServiceImpl
    private lateinit var thresholdCheck: BehaviorThresholdCheck
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
    fun `test new normalized DeviceId`() {
        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFC000000" }
        assertEquals(0.0, thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)

        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFCFFFFFF" }
        assertEquals(100.0, thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)

        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFC0D0739" }
        assertEquals(5.08, thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)

        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFCED0739" }
        assertEquals(92.58, thresholdCheck.getNormalizedDeviceId().toDouble(), 0.01)
    }

    @Test
    fun `test isBehaviourEnabled`() {
        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFC000000" }
        assertFalse(thresholdCheck.isBehaviorEnabled(0.0f))
        assertTrue(thresholdCheck.isBehaviorEnabled(0.1f))
        assertTrue(thresholdCheck.isBehaviorEnabled(100.0f))
        assertTrue(thresholdCheck.isBehaviorEnabled(99.9f))
        assertTrue(thresholdCheck.isBehaviorEnabled(34.9f))

        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFCFFFFFF" }
        assertFalse(thresholdCheck.isBehaviorEnabled(99.9f))
        assertTrue(thresholdCheck.isBehaviorEnabled(100.0f))

        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFC0D0739" }
        assertFalse(thresholdCheck.isBehaviorEnabled(0.0f))
        assertFalse(thresholdCheck.isBehaviorEnabled(2.0f))
        assertFalse(thresholdCheck.isBehaviorEnabled(5.0f))
        assertFalse(thresholdCheck.isBehaviorEnabled(5.07f))
        assertTrue(thresholdCheck.isBehaviorEnabled(5.09f))
        assertTrue(thresholdCheck.isBehaviorEnabled(47.92f))
        assertTrue(thresholdCheck.isBehaviorEnabled(100.0f))
    }

    @Test
    fun `test isBehaviourEnabled with bad input`() {
        thresholdCheck = BehaviorThresholdCheck { "07D85B44E4E245F4A30E559BFCFFFFFF" }
        assertFalse(thresholdCheck.isBehaviorEnabled(1000f))
        assertFalse(thresholdCheck.isBehaviorEnabled(-1000f))
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

    /**
     * Create a new instance of the [ConfigServiceImpl]
     */
    private fun createService(
        hasConfiguredExporters: () -> Boolean = { false },
        appId: String? = "AbCdE",
    ): ConfigServiceImpl = ConfigServiceImpl(
        instrumentedConfig = FakeInstrumentedConfig(project = FakeProjectConfig(appId = appId)),
        worker = fakeBackgroundWorker(),
        serializer = serializer,
        store = FakeDeviceIdStore(),
        okHttpClient = okHttpClient,
        abis = arrayOf("arm64-v8a"),
        sdkVersion = "1.2.3",
        apiLevel = 36,
        filesDir = Files.createTempDirectory("tmp").toFile(),
        logger = FakeInternalLogger(),
        hasConfiguredOtlpExport = hasConfiguredExporters,
    )
}
