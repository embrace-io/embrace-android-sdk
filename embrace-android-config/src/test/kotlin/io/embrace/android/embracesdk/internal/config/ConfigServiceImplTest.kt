package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.fakes.FAKE_DEVICE_ID
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.payload.AppFramework
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class ConfigServiceImplTest {

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
        deviceIdSupplier: () -> String = { FAKE_DEVICE_ID },
        hasConfiguredExporters: () -> Boolean = { false },
        appId: String? = "AbCdE",
    ): ConfigServiceImpl = ConfigServiceImpl(
        hasConfiguredOtelExporters = hasConfiguredExporters,
        deviceIdSupplier = deviceIdSupplier,
        instrumentedConfig = FakeInstrumentedConfig(project = FakeProjectConfig(appId = appId)),
        remoteConfig = remoteConfig,
    )
}
