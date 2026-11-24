package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
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
            assertEquals(250, spanLimit)
            assertEquals(50L, interactionResponsivenessBlockThresholdMs)
        }
        assertEquals(defaults.snapshot(), resolved.snapshot())
    }

    @Test
    fun `remote values override defaults`() {
        val json = """
            {
              "vitals": {
                "smoothness_idle_threshold_ms": 150,
                "smoothness_held_idle_threshold_ms": 600,
                "jank_heuristic_multiplier": 2.5,
                "screen_load_idle_threshold_ms": 1500,
                "screen_load_timeout_ms": 45000,
                "screen_load_nav_timeout_ms": 750,
                "smoothness_frame_trace_pct_enabled": 100,
                "span_limit": 25,
                "interaction_responsiveness_block_threshold_ms": 75
              }
            }
        """.trimIndent()
        val remote = TestPlatformSerializer().fromJson(json, RemoteConfig.serializer())
        with(resolveVitals(remote)) {
            assertEquals(150L, smoothnessIdleThresholdMs)
            assertEquals(600L, smoothnessHeldIdleThresholdMs)
            assertEquals(2.5, jankHeuristicMultiplier, 0.0)
            assertEquals(1500L, screenLoadIdleThresholdMs)
            assertEquals(45_000L, screenLoadTimeoutMs)
            assertEquals(750L, screenLoadNavTimeoutMs)
            assertEquals(25, spanLimit)
            assertEquals(75L, interactionResponsivenessBlockThresholdMs)
        }
    }

    @Test
    fun `legacy frame trace key in json is ignored`() {
        // smoothness_frame_trace_pct_enabled was removed, but config served or persisted by older builds may still
        // contain it, so it must not make the JSON invalid.
        val json = """
            {
              "vitals": {
                "smoothness_frame_trace_pct_enabled": 50,
                "span_limit": 25
              }
            }
        """.trimIndent()
        val remote = TestPlatformSerializer().fromJson(json, RemoteConfig.serializer())
        assertEquals(25, resolveVitals(remote).spanLimit)
    }

    private fun VitalsConfig.snapshot() = listOf(
        smoothnessIdleThresholdMs,
        smoothnessHeldIdleThresholdMs,
        jankHeuristicMultiplier,
        screenLoadIdleThresholdMs,
        screenLoadTimeoutMs,
        screenLoadNavTimeoutMs,
        spanLimit,
        interactionResponsivenessBlockThresholdMs,
    )
}
