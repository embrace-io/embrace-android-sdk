package io.embrace.android.embracesdk.internal.config.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration values relating to the thresholds used by the vitals (smoothness / screen-load) feature.
 */
@Serializable
data class VitalsRemoteConfig(

    @SerialName("smoothness_idle_threshold_ms")
    val smoothnessIdleThresholdMs: Long? = null,

    @SerialName("smoothness_held_idle_threshold_ms")
    val smoothnessHeldIdleThresholdMs: Long? = null,

    @SerialName("jank_heuristic_multiplier")
    val jankHeuristicMultiplier: Double? = null,

    @SerialName("screen_load_idle_threshold_ms")
    val screenLoadIdleThresholdMs: Long? = null,

    @SerialName("screen_load_timeout_ms")
    val screenLoadTimeoutMs: Long? = null,

    @SerialName("screen_load_nav_timeout_ms")
    val screenLoadNavTimeoutMs: Long? = null,
)
