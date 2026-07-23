package io.embrace.android.embracesdk.fakes.behavior

import io.embrace.android.embracesdk.internal.config.behavior.VitalsBehavior

class FakeVitalsBehavior(
    var smoothnessIdleThresholdMsImpl: Long = 100L,
    var smoothnessHeldIdleThresholdMsImpl: Long = 500L,
    var jankHeuristicMultiplierImpl: Double = 2.0,
    var screenLoadIdleThresholdMsImpl: Long = 1000L,
    var screenLoadTimeoutMsImpl: Long = 30_000L,
    var screenLoadNavTimeoutMsImpl: Long = 500L,
    var smoothnessFrameTraceEnabledImpl: Boolean = false,
) : VitalsBehavior {

    override fun getSmoothnessIdleThresholdMs(): Long = smoothnessIdleThresholdMsImpl
    override fun getSmoothnessHeldIdleThresholdMs(): Long = smoothnessHeldIdleThresholdMsImpl
    override fun getJankHeuristicMultiplier(): Double = jankHeuristicMultiplierImpl
    override fun getScreenLoadIdleThresholdMs(): Long = screenLoadIdleThresholdMsImpl
    override fun getScreenLoadTimeoutMs(): Long = screenLoadTimeoutMsImpl
    override fun getScreenLoadNavTimeoutMs(): Long = screenLoadNavTimeoutMsImpl
    override fun isSmoothnessFrameTraceEnabled(): Boolean = smoothnessFrameTraceEnabledImpl
}
