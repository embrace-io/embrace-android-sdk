package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.FAKE_DEVICE_ID
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.createAutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.config.remote.DataRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.KillSwitchRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AutoDataCaptureBehaviorImplTest {

    private val remote = RemoteConfig(
        killSwitchConfig = KillSwitchRemoteConfig(
            sigHandlerDetection = true,
            jetpackCompose = false,
        ),
        dataConfig = DataRemoteConfig(pctThermalStatusEnabled = 0.0f),
        uiLoadInstrumentationEnabled = false,
    )

    @Test
    fun testDefaults() {
        with(createAutoDataCaptureBehavior()) {
            assertTrue(isPowerSaveModeCaptureEnabled())
            assertTrue(isNetworkConnectivityCaptureEnabled())
            assertTrue(isThreadBlockageCaptureEnabled())
            assertTrue(isJvmCrashCaptureEnabled())
            assertFalse(isComposeClickCaptureEnabled())
            assertFalse(is3rdPartySigHandlerDetectionEnabled())
            assertTrue(isNativeCrashCaptureEnabled())
            assertTrue(isDiskUsageCaptureEnabled())
            assertTrue(isThermalStatusCaptureEnabled())
            assertTrue(isUiLoadTracingEnabled())
            assertTrue(isUiLoadTracingTraceAll())
            assertTrue(isThermalStatusCaptureEnabled())
            assertFalse(isEndStartupWithAppReadyEnabled())
            assertFalse(isStateCaptureEnabled())
        }
    }

    @Test
    fun testLocalAndRemote() {
        with(createAutoDataCaptureBehavior(remoteCfg = remote)) {
            assertTrue(is3rdPartySigHandlerDetectionEnabled())
            assertFalse(isComposeClickCaptureEnabled())
            assertFalse(isThermalStatusCaptureEnabled())
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

    @Test
    fun `enable state capture`() {
        val behavior = createBehavior(
            localUiLoadTracingEnabled = true,
            localUiLoadTracingTraceAllEnabled = true,
            stateCaptureEnabled = true,
            remote = remote
        )

        assertTrue(behavior.isStateCaptureEnabled())
    }

    @Test
    fun `enable state capture remotely`() {
        val behavior = createBehavior(
            localUiLoadTracingEnabled = true,
            localUiLoadTracingTraceAllEnabled = true,
            stateCaptureEnabled = false,
            remote = remote.copy(pctStateCaptureEnabledV2 = 100.0f)
        )

        assertTrue(behavior.isStateCaptureEnabled())
    }

    @Test
    fun `disable state capture remotely`() {
        val behavior = createBehavior(
            localUiLoadTracingEnabled = true,
            localUiLoadTracingTraceAllEnabled = true,
            stateCaptureEnabled = true,
            remote = remote.copy(pctStateCaptureEnabledV2 = 0.0f)
        )

        assertFalse(behavior.isStateCaptureEnabled())
    }

    private fun createBehavior(
        localUiLoadTracingEnabled: Boolean,
        localUiLoadTracingTraceAllEnabled: Boolean,
        stateCaptureEnabled: Boolean = false,
        remote: RemoteConfig,
    ) = AutoDataCaptureBehaviorImpl(
        thresholdCheck = BehaviorThresholdCheck { FAKE_DEVICE_ID },
        local = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(
                uiLoadTracingTraceAll = localUiLoadTracingTraceAllEnabled,
                uiLoadTracingEnabled = localUiLoadTracingEnabled,
                stateCaptureEnabled = stateCaptureEnabled
            )
        ),
        remote = remote
    )
}
