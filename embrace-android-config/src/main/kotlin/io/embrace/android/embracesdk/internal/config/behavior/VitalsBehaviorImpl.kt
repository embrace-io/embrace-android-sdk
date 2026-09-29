package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.VitalsConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveVitals

/**
 * Provides the behavior that the vitals (smoothness / screen-load) feature should follow.
 */
class VitalsBehaviorImpl(private val config: VitalsConfig) : VitalsBehavior {

    constructor(thresholdCheck: BehaviorThresholdCheck, remote: RemoteConfig?) :
        this(resolveVitals(behaviorInputs(InstrumentedConfigImpl, remote, thresholdCheck)))

    override fun getSmoothnessIdleThresholdMs(): Long = config.smoothnessIdleThresholdMs
    override fun getSmoothnessHeldIdleThresholdMs(): Long = config.smoothnessHeldIdleThresholdMs
    override fun getJankHeuristicMultiplier(): Double = config.jankHeuristicMultiplier
    override fun getScreenLoadIdleThresholdMs(): Long = config.screenLoadIdleThresholdMs
    override fun getScreenLoadTimeoutMs(): Long = config.screenLoadTimeoutMs
    override fun getScreenLoadNavTimeoutMs(): Long = config.screenLoadNavTimeoutMs
    override fun isSmoothnessFrameTraceEnabled(): Boolean = config.smoothnessFrameTraceEnabled
    override fun getSpanLimit(): Int = config.spanLimit
}
