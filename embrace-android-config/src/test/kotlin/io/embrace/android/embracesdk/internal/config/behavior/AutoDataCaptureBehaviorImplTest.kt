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
            assertTrue(isStateCaptureEnabled())
            assertFalse(isNetworkCallbackConnectivityServiceEnabled())
            assertTrue(isNavigationStateCaptureEnabled())
            assertFalse(isSmoothnessCaptureEnabled())
            assertFalse(isActivityProcessLifecycleTrackerEnabled())
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
                jetpackCompose = true,
            ),
        )

        // Jetpack Compose enabled remotely
        with(
            createAutoDataCaptureBehavior(
                remoteCfg = remoteComposeKillSwitchOff,
            ),
        ) {
            assertTrue(isComposeClickCaptureEnabled())
        }
    }

    @Test
    fun `disable ui load remotely`() {
        val behavior = createBehavior(
            localUiLoadTracingEnabled = true,
            localUiLoadTracingTraceAllEnabled = true,
            remote = remote.copy(uiLoadInstrumentationEnabled = false),
        )

        assertFalse(behavior.isUiLoadTracingEnabled())
        assertFalse(behavior.isUiLoadTracingTraceAll())
    }

    @Test
    fun `disable ui load locally`() {
        val behavior = createBehavior(
            localUiLoadTracingEnabled = false,
            localUiLoadTracingTraceAllEnabled = false,
            remote = remote.copy(uiLoadInstrumentationEnabled = true),
        )

        assertFalse(behavior.isUiLoadTracingEnabled())
        assertFalse(behavior.isUiLoadTracingTraceAll())
    }

    @Test
    fun `disable ui load trace all locally`() {
        val behavior = createBehavior(
            localUiLoadTracingEnabled = true,
            localUiLoadTracingTraceAllEnabled = false,
            remote = remote.copy(uiLoadInstrumentationEnabled = true),
        )

        assertTrue(behavior.isUiLoadTracingEnabled())
        assertFalse(behavior.isUiLoadTracingTraceAll())
    }

    @Test
    fun `enable ui load trace all locally`() {
        val behavior = createBehavior(
            localUiLoadTracingEnabled = true,
            localUiLoadTracingTraceAllEnabled = false,
            remote = remote.copy(uiLoadInstrumentationEnabled = true),
        )

        assertTrue(behavior.isUiLoadTracingEnabled())
        assertFalse(behavior.isUiLoadTracingTraceAll())
    }

    @Test
    fun `enable state capture remotely`() {
        val behavior = createBehavior(
            localUiLoadTracingEnabled = true,
            localUiLoadTracingTraceAllEnabled = true,
            remote = remote.copy(pctStateCaptureEnabledV2 = 100.0f),
        )

        assertTrue(behavior.isStateCaptureEnabled())
    }

    @Test
    fun `disable state capture remotely`() {
        val behavior = createBehavior(
            localUiLoadTracingEnabled = true,
            localUiLoadTracingTraceAllEnabled = true,
            remote = remote.copy(pctStateCaptureEnabledV2 = 0.0f),
        )

        assertFalse(behavior.isStateCaptureEnabled())
    }

    @Test
    fun `isNetworkCallbackConnectivityServiceEnabled false when remote field null`() {
        assertFalse(createAutoDataCaptureBehavior(remoteCfg = null).isNetworkCallbackConnectivityServiceEnabled())
    }

    @Test
    fun `isNetworkCallbackConnectivityServiceEnabled true when pct is 100`() {
        assertTrue(
            createAutoDataCaptureBehavior(remoteCfg = RemoteConfig(pctNetworkCallbackConnectivityServiceEnabled = 100.0f))
                .isNetworkCallbackConnectivityServiceEnabled(),
        )
    }

    @Test
    fun `isNetworkCallbackConnectivityServiceEnabled false when pct is 0`() {
        assertFalse(
            createAutoDataCaptureBehavior(remoteCfg = RemoteConfig(pctNetworkCallbackConnectivityServiceEnabled = 0.0f))
                .isNetworkCallbackConnectivityServiceEnabled(),
        )
    }

    @Test
    fun `navigation state capture enabled when pct is 100`() {
        assertTrue(
            createBehavior(remote = RemoteConfig(pctNavigationStateCaptureEnabled = 100.0f))
                .isNavigationStateCaptureEnabled(),
        )
    }

    @Test
    fun `navigation state capture disabled when pct is 0`() {
        assertFalse(
            createBehavior(remote = RemoteConfig(pctNavigationStateCaptureEnabled = 0.0f))
                .isNavigationStateCaptureEnabled(),
        )
    }

    @Test
    fun `smoothness capture disabled when remote field null`() {
        assertFalse(createAutoDataCaptureBehavior(remoteCfg = null).isSmoothnessCaptureEnabled())
    }

    @Test
    fun `smoothness capture enabled when pct is 100`() {
        assertTrue(
            createBehavior(remote = RemoteConfig(pctSmoothnessEnabled = 100.0f))
                .isSmoothnessCaptureEnabled(),
        )
    }

    @Test
    fun `smoothness capture disabled when pct is 0`() {
        assertFalse(
            createBehavior(remote = RemoteConfig(pctSmoothnessEnabled = 0.0f))
                .isSmoothnessCaptureEnabled(),
        )
    }

    @Test
    fun `activity process lifecycle tracker disabled by default`() {
        assertFalse(createAutoDataCaptureBehavior(remoteCfg = null).isActivityProcessLifecycleTrackerEnabled())
    }

    @Test
    fun `activity process lifecycle tracker falls back to local flag when remote field null`() {
        assertTrue(
            createBehavior(localActivityProcessLifecycleTrackerEnabled = true, remote = RemoteConfig())
                .isActivityProcessLifecycleTrackerEnabled(),
        )
    }

    @Test
    fun `activity process lifecycle tracker enabled when pct is 100`() {
        assertTrue(
            createBehavior(remote = RemoteConfig(pctActivityProcessLifecycleTrackerEnabled = 100.0f))
                .isActivityProcessLifecycleTrackerEnabled(),
        )
    }

    @Test
    fun `activity process lifecycle tracker remote pct of 0 overrides enabled local flag`() {
        assertFalse(
            createBehavior(
                localActivityProcessLifecycleTrackerEnabled = true,
                remote = RemoteConfig(pctActivityProcessLifecycleTrackerEnabled = 0.0f),
            ).isActivityProcessLifecycleTrackerEnabled(),
        )
    }

    private fun createBehavior(
        localUiLoadTracingEnabled: Boolean = true,
        localUiLoadTracingTraceAllEnabled: Boolean = true,
        localActivityProcessLifecycleTrackerEnabled: Boolean = false,
        remote: RemoteConfig,
    ) = AutoDataCaptureBehaviorImpl(
        thresholdCheck = BehaviorThresholdCheck { FAKE_DEVICE_ID },
        local = FakeInstrumentedConfig(
            enabledFeatures = FakeEnabledFeatureConfig(
                uiLoadTracingTraceAll = localUiLoadTracingTraceAllEnabled,
                uiLoadTracingEnabled = localUiLoadTracingEnabled,
                activityProcessLifecycleTracker = localActivityProcessLifecycleTrackerEnabled,
            ),
        ),
        remote = remote,
    )
}
