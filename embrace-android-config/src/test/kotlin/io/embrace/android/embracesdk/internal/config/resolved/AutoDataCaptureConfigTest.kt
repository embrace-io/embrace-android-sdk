package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.DataRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.KillSwitchRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AutoDataCaptureConfigTest {

    @Test
    fun `defaults match resolved default config`() {
        val defaults = EmbraceConfig().autoDataCapture
        val resolved = resolveConfig(InstrumentedConfigImpl, null, unreadBucket).autoDataCapture
        with(defaults) {
            assertTrue(thermalStatusCaptureEnabled)
            assertTrue(powerSaveModeCaptureEnabled)
            assertTrue(networkConnectivityCaptureEnabled)
            assertTrue(threadBlockageCaptureEnabled)
            assertTrue(jvmCrashCaptureEnabled)
            assertFalse(composeClickCaptureEnabled)
            assertFalse(thirdPartySigHandlerDetectionEnabled)
            assertTrue(nativeCrashCaptureEnabled)
            assertTrue(diskUsageCaptureEnabled)
            assertTrue(uiLoadTracingEnabled)
            assertTrue(uiLoadTracingTraceAll)
            assertFalse(endStartupWithAppReadyEnabled)
            assertTrue(stateCaptureEnabled)
            assertFalse(networkCallbackConnectivityServiceEnabled)
            assertTrue(navigationStateCaptureEnabled)
            assertFalse(smoothnessCaptureEnabled)
            assertFalse(screenLoadCaptureEnabled)
            assertFalse(activityProcessLifecycleTrackerEnabled)
            assertFalse(activityLeakDetectionEnabled)
            assertFalse(fragmentLeakDetectionEnabled)
            assertFalse(webViewLeakDetectionEnabled)
        }
        assertEquals(defaults.snapshot(), resolved.snapshot())
    }

    @Test
    fun `local flags are read`() {
        val local = FakeEnabledFeatureConfig(
            powerSaveCapture = false,
            networkConnectivityCapture = false,
            threadBlockageCapture = false,
            jvmCrashCapture = false,
            nativeCrashCapture = false,
            diskUsageCapture = false,
            endStartupWithAppReady = true,
        )
        with(resolve(local)) {
            assertFalse(powerSaveModeCaptureEnabled)
            assertFalse(networkConnectivityCaptureEnabled)
            assertFalse(threadBlockageCaptureEnabled)
            assertFalse(jvmCrashCaptureEnabled)
            assertFalse(nativeCrashCaptureEnabled)
            assertFalse(diskUsageCaptureEnabled)
            assertTrue(endStartupWithAppReadyEnabled)
        }
    }

    @Test
    fun `kill switches override local flags`() {
        val local = FakeEnabledFeatureConfig(composeClickCapture = false, sigHandlerDetection = false)
        assertFalse(resolve(local).composeClickCaptureEnabled)
        val killSwitch = KillSwitchRemoteConfig(sigHandlerDetection = true, jetpackCompose = true)
        with(resolve(local, RemoteConfig(killSwitchConfig = killSwitch))) {
            assertTrue(composeClickCaptureEnabled)
            assertTrue(thirdPartySigHandlerDetectionEnabled)
        }
    }

    @Test
    fun `ui load tracing requires both local and remote enablement`() {
        val local = FakeEnabledFeatureConfig(uiLoadTracingEnabled = true, uiLoadTracingTraceAll = false)
        with(resolve(local, RemoteConfig(uiLoadInstrumentationEnabled = true))) {
            assertTrue(uiLoadTracingEnabled)
            assertFalse(uiLoadTracingTraceAll)
        }
        val allLocal = FakeEnabledFeatureConfig(uiLoadTracingEnabled = true, uiLoadTracingTraceAll = true)
        with(resolve(allLocal, RemoteConfig(uiLoadInstrumentationEnabled = false))) {
            assertFalse(uiLoadTracingEnabled)
            assertFalse(uiLoadTracingTraceAll)
        }
    }

    @Test
    fun `activity process lifecycle tracker falls back to local flag`() {
        val local = FakeEnabledFeatureConfig(activityProcessLifecycleTracker = true)
        assertTrue(resolve(local, RemoteConfig()).activityProcessLifecycleTrackerEnabled)
        val remote = RemoteConfig(pctActivityProcessLifecycleTrackerEnabled = 0f)
        assertFalse(resolve(local, remote).activityProcessLifecycleTrackerEnabled)
    }

    @Test
    fun `remote pct of 100 enables all rollouts`() {
        assertEquals(List(ROLLOUT_COUNT) { true }, resolveRollouts(100f, unreadBucket))
    }

    @Test
    fun `remote pct of 0 disables all rollouts`() {
        assertEquals(List(ROLLOUT_COUNT) { false }, resolveRollouts(0f, unreadBucket))
    }

    @Test
    fun `partial rollout reads bucket`() {
        assertEquals(List(ROLLOUT_COUNT) { true }, resolveRollouts(50f, lazy { 49f }))
        assertEquals(List(ROLLOUT_COUNT) { false }, resolveRollouts(50f, lazy { 51f }))
    }

    private fun resolve(local: FakeEnabledFeatureConfig, remote: RemoteConfig? = null) = resolveAutoDataCapture(
        FakeInstrumentedConfig(enabledFeatures = local),
        remote,
        unreadBucket,
    )

    private fun resolveRollouts(pct: Float, bucket: Lazy<Float>): List<Boolean> {
        val remote = RemoteConfig(
            dataConfig = DataRemoteConfig(pctThermalStatusEnabled = pct),
            pctStateCaptureEnabledV2 = pct,
            pctNetworkCallbackConnectivityServiceEnabled = pct,
            pctNavigationStateCaptureEnabled = pct,
            pctSmoothnessEnabled = pct,
            pctScreenLoadEnabled = pct,
            pctActivityProcessLifecycleTrackerEnabled = pct,
            pctActivityLeakDetectionEnabled = pct,
            pctFragmentLeakDetectionEnabled = pct,
            pctWebViewLeakDetectionEnabled = pct,
        )
        return with(resolveAutoDataCapture(InstrumentedConfigImpl, remote, bucket)) {
            listOf(
                thermalStatusCaptureEnabled,
                stateCaptureEnabled,
                networkCallbackConnectivityServiceEnabled,
                navigationStateCaptureEnabled,
                smoothnessCaptureEnabled,
                screenLoadCaptureEnabled,
                activityProcessLifecycleTrackerEnabled,
                activityLeakDetectionEnabled,
                fragmentLeakDetectionEnabled,
                webViewLeakDetectionEnabled,
            )
        }
    }

    private fun AutoDataCaptureConfig.snapshot() = listOf(
        thermalStatusCaptureEnabled,
        powerSaveModeCaptureEnabled,
        networkConnectivityCaptureEnabled,
        threadBlockageCaptureEnabled,
        jvmCrashCaptureEnabled,
        composeClickCaptureEnabled,
        thirdPartySigHandlerDetectionEnabled,
        nativeCrashCaptureEnabled,
        diskUsageCaptureEnabled,
        uiLoadTracingEnabled,
        uiLoadTracingTraceAll,
        endStartupWithAppReadyEnabled,
        stateCaptureEnabled,
        networkCallbackConnectivityServiceEnabled,
        navigationStateCaptureEnabled,
        smoothnessCaptureEnabled,
        screenLoadCaptureEnabled,
        activityProcessLifecycleTrackerEnabled,
        activityLeakDetectionEnabled,
        fragmentLeakDetectionEnabled,
        webViewLeakDetectionEnabled,
    )

    private companion object {
        const val ROLLOUT_COUNT = 10
    }
}
