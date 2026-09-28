package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.OtelConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveOtel

/**
 * Provides the behavior for OpenTelemetry configuration
 */
class OtelBehaviorImpl(private val config: OtelConfig) : OtelBehavior {

    constructor(thresholdCheck: BehaviorThresholdCheck, local: InstrumentedConfig, remote: RemoteConfig?) :
        this(resolveOtel(behaviorInputs(local, remote, thresholdCheck)))

    override fun shouldUseKotlinSdk(): Boolean = config.kotlinSdkEnabled
    override fun getMaxCustomSpansPerSessionPart(): Int = config.maxCustomSpansPerSessionPart
    override fun getMaxInternalSpansPerSessionPart(): Int = config.maxInternalSpansPerSessionPart
    override fun getMaxNetworkSpansPerSessionPart(): Int = config.maxNetworkSpansPerSessionPart
    override fun getMaxSpanEventsPerSessionPart(): Int = config.maxSpanEventsPerSessionPart
    override fun getPeriodicCacheIntervalMs(): Long = config.periodicCacheIntervalMs
}
