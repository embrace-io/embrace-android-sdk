package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.createAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.config.remote.DataRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.KillSwitchRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Uuid
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AutoDataCaptureBehaviorImplTest {

    private val remote = RemoteConfig(
        killSwitchConfig = KillSwitchRemoteConfig(
            sigHandlerDetection = true,
            jetpackCompose = false,
            v2StoragePct = 100f,
            useOkHttpPct = 100f
        ),
        dataConfig = DataRemoteConfig(pctThermalStatusEnabled = 0.0f),
        uiLoadInstrumentationEnabled = false,
    )

    @Test
    fun testDefaults() {
        with(createAutoDataCaptureBehavior()) {
            assertTrue(isMemoryWarningCaptureEnabled())
            assertTrue(isPowerSaveModeCaptureEnabled())
            assertTrue(isNetworkConnectivityCaptureEnabled())
            assertTrue(isAnrCaptureEnabled())
            assertTrue(isJvmCrashCaptureEnabled())
            assertFalse(isComposeClickCaptureEnabled())
            assertFalse(is3rdPartySigHandlerDetectionEnabled())
            assertFalse(isNativeCrashCaptureEnabled())
            assertTrue(isDiskUsageCaptureEnabled())
            assertTrue(isThermalStatusCaptureEnabled())
            assertFalse(isUiLoadTracingEnabled())
            assertFalse(isUiLoadTracingTraceAll())
            assertTrue(isThermalStatusCaptureEnabled())
            assertTrue(isV2StorageEnabled())
            assertFalse(isEndStartupWithAppReadyEnabled())
        }
    }

    @Test
    fun testLocalAndRemote() {
        with(createAutoDataCaptureBehavior(remoteCfg = remote)) {
            assertTrue(is3rdPartySigHandlerDetectionEnabled())
            assertFalse(isComposeClickCaptureEnabled())
            assertFalse(isThermalStatusCaptureEnabled())
            assertTrue(isV2StorageEnabled())
        }
    }

    @Test
    fun testJetpackCompose() {
        // Jetpack Compose is disabled by default
        with(createAutoDataCaptureBehavior()) {
            assertFalse(isComposeClickCaptureEnabled())
        }

        // Jetpack Compose disabled remotely
        with(createAutoDataCaptureBehavior(remoteCfg = remote)) {
            assertFalse(isComposeClickCaptureEnabled())
        }
        val remoteComposeKillSwitchOff = RemoteConfig(
            killSwitchConfig = KillSwitchRemoteConfig(
                sigHandlerDetection = false,
                jetpackCompose = true
            )
        )

        // Jetpack Compose enabled remotely
        with(
            createAutoDataCaptureBehavior(
                remoteCfg = remoteComposeKillSwitchOff
            )
        ) {
            assertTrue(isComposeClickCaptureEnabled())
        }
    }

    @Test
    fun `disable ui load remotely`() {
        val behavior = createBehavior(
            localUiLoadTracingEnabled = true,
            localUiLoadTracingTraceAllEnabled = true,
            remote = remote.copy(uiLoadInstrumentationEnabled = false)
        )

        assertFalse(behavior.isUiLoadTracingEnabled())
        assertFalse(behavior.isUiLoadTracingTraceAll())
    }

    @Test
    fun `disable ui load locally`() {
        val behavior = createBehavior(
            localUiLoadTracingEnabled = false,
            localUiLoadTracingTraceAllEnabled = false,
            remote = remote.copy(uiLoadInstrumentationEnabled = true)
        )

        assertFalse(behavior.isUiLoadTracingEnabled())
        assertFalse(behavior.isUiLoadTracingTraceAll())
    }

    @Test
    fun `disable ui load trace all locally`() {
        val behavior = createBehavior(
            localUiLoadTracingEnabled = true,
            localUiLoadTracingTraceAllEnabled = false,
            remote = remote.copy(uiLoadInstrumentationEnabled = true)
        )

        assertTrue(behavior.isUiLoadTracingEnabled())
        assertFalse(behavior.isUiLoadTracingTraceAll())
    }

    @Test
    fun `enable ui load trace all locally`() {
        val behavior = createBehavior(
            localUiLoadTracingEnabled = true,
            localUiLoadTracingTraceAllEnabled = false,
            remote = remote.copy(uiLoadInstrumentationEnabled = true)
        )

        assertTrue(behavior.isUiLoadTracingEnabled())
        assertFalse(behavior.isUiLoadTracingTraceAll())
    }

    private fun createBehavior(
        localUiLoadTracingEnabled: Boolean,
        localUiLoadTracingTraceAllEnabled: Boolean,
        remote: RemoteConfig,
    ) = AutoDataCaptureBehaviorImpl(
        thresholdCheck = BehaviorThresholdCheck(Uuid::getEmbUuid),
        local = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(
                uiLoadTracingTraceAll = localUiLoadTracingTraceAllEnabled,
                uiLoadTracingEnabled = localUiLoadTracingEnabled
            )
        ),
        remote = remote
    )
}
