package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.VitalsRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class VitalsConfigTest {

    @Test
    fun `defaults match resolved default config`() {
        val defaults = EmbraceConfig().vitals
        val resolved = resolveConfig(InstrumentedConfigImpl, null, unreadBucket).vitals
        with(defaults) {
            assertEquals(100L, smoothnessIdleThresholdMs)
            assertEquals(500L, smoothnessHeldIdleThresholdMs)
            assertEquals(2.0, jankHeuristicMultiplier, 0.0)
            assertEquals(1000L, screenLoadIdleThresholdMs)
            assertEquals(30_000L, screenLoadTimeoutMs)
            assertEquals(500L, screenLoadNavTimeoutMs)
            assertFalse(smoothnessFrameTraceEnabled)
            assertEquals(250, spanLimit)
        }
        assertEquals(defaults.snapshot(), resolved.snapshot())
    }

    @Test
    fun `remote values override defaults`() {
        val remote = VitalsRemoteConfig(
            smoothnessIdleThresholdMs = 150,
            smoothnessHeldIdleThresholdMs = 600,
            jankHeuristicMultiplier = 2.5,
            screenLoadIdleThresholdMs = 1500,
            screenLoadTimeoutMs = 45_000,
            screenLoadNavTimeoutMs = 750,
            smoothnessFrameTracePctEnabled = 100f,
            spanLimit = 25,
        )
        with(resolveVitals(RemoteConfig(vitalsRemoteConfig = remote), unreadBucket)) {
            assertEquals(150L, smoothnessIdleThresholdMs)
            assertEquals(600L, smoothnessHeldIdleThresholdMs)
            assertEquals(2.5, jankHeuristicMultiplier, 0.0)
            assertEquals(1500L, screenLoadIdleThresholdMs)
            assertEquals(45_000L, screenLoadTimeoutMs)
            assertEquals(750L, screenLoadNavTimeoutMs)
            assertTrue(smoothnessFrameTraceEnabled)
            assertEquals(25, spanLimit)
        }
    }

    @Test
    fun `partial frame trace rollout reads bucket`() {
        val remote = RemoteConfig(vitalsRemoteConfig = VitalsRemoteConfig(smoothnessFrameTracePctEnabled = 50f))
        assertTrue(resolveVitals(remote, lazy { 49f }).smoothnessFrameTraceEnabled)
        assertFalse(resolveVitals(remote, lazy { 51f }).smoothnessFrameTraceEnabled)
    }

    private fun VitalsConfig.snapshot() = listOf(
        smoothnessIdleThresholdMs,
        smoothnessHeldIdleThresholdMs,
        jankHeuristicMultiplier,
        screenLoadIdleThresholdMs,
        screenLoadTimeoutMs,
        screenLoadNavTimeoutMs,
        smoothnessFrameTraceEnabled,
        spanLimit,
    )
}
