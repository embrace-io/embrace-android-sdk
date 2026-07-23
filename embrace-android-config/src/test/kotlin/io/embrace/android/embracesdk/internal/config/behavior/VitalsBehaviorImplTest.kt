package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createVitalsBehavior
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.VitalsRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class VitalsBehaviorImplTest {

    private val remote = VitalsRemoteConfig(
        smoothnessIdleThresholdMs = 150,
        smoothnessHeldIdleThresholdMs = 600,
        jankHeuristicMultiplier = 2.5,
        screenLoadIdleThresholdMs = 1500,
        screenLoadTimeoutMs = 45_000,
        screenLoadNavTimeoutMs = 750,
        smoothnessFrameTracePctEnabled = 100f,
    )

    @Test
    fun testDefaults() {
        with(createVitalsBehavior()) {
            assertEquals(100L, getSmoothnessIdleThresholdMs())
            assertEquals(500L, getSmoothnessHeldIdleThresholdMs())
            assertEquals(2.0, getJankHeuristicMultiplier(), 0.0)
            assertEquals(1000L, getScreenLoadIdleThresholdMs())
            assertEquals(30_000L, getScreenLoadTimeoutMs())
            assertEquals(500L, getScreenLoadNavTimeoutMs())
            assertFalse(isSmoothnessFrameTraceEnabled())
        }
    }

    @Test
    fun testRemote() {
        with(createVitalsBehavior(remoteCfg = RemoteConfig(vitalsRemoteConfig = remote))) {
            assertEquals(150L, getSmoothnessIdleThresholdMs())
            assertEquals(600L, getSmoothnessHeldIdleThresholdMs())
            assertEquals(2.5, getJankHeuristicMultiplier(), 0.0)
            assertEquals(1500L, getScreenLoadIdleThresholdMs())
            assertEquals(45_000L, getScreenLoadTimeoutMs())
            assertEquals(750L, getScreenLoadNavTimeoutMs())
            assertTrue(isSmoothnessFrameTraceEnabled())
        }
    }
}
