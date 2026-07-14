package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * Provides the behavior that the vitals (smoothness / screen-load) feature should follow.
 */
class VitalsBehaviorImpl(
    remote: RemoteConfig?,
) : VitalsBehavior {

    private companion object {
        private const val DEFAULT_SMOOTHNESS_IDLE_THRESHOLD_MS = 100L
        private const val DEFAULT_SMOOTHNESS_HELD_IDLE_THRESHOLD_MS = 500L
        private const val DEFAULT_JANK_HEURISTIC_MULTIPLIER = 2.0
        private const val DEFAULT_SCREEN_LOAD_IDLE_THRESHOLD_MS = 1000L
        private const val DEFAULT_SCREEN_LOAD_TIMEOUT_MS = 30_000L
        private const val DEFAULT_SCREEN_LOAD_NAV_TIMEOUT_MS = 500L
    }

    private val remote = remote?.vitalsRemoteConfig

    override fun getSmoothnessIdleThresholdMs(): Long =
        remote?.smoothnessIdleThresholdMs ?: DEFAULT_SMOOTHNESS_IDLE_THRESHOLD_MS

    override fun getSmoothnessHeldIdleThresholdMs(): Long =
        remote?.smoothnessHeldIdleThresholdMs ?: DEFAULT_SMOOTHNESS_HELD_IDLE_THRESHOLD_MS

    override fun getJankHeuristicMultiplier(): Double =
        remote?.jankHeuristicMultiplier ?: DEFAULT_JANK_HEURISTIC_MULTIPLIER

    override fun getScreenLoadIdleThresholdMs(): Long =
        remote?.screenLoadIdleThresholdMs ?: DEFAULT_SCREEN_LOAD_IDLE_THRESHOLD_MS

    override fun getScreenLoadTimeoutMs(): Long =
        remote?.screenLoadTimeoutMs ?: DEFAULT_SCREEN_LOAD_TIMEOUT_MS

    override fun getScreenLoadNavTimeoutMs(): Long =
        remote?.screenLoadNavTimeoutMs ?: DEFAULT_SCREEN_LOAD_NAV_TIMEOUT_MS
}
