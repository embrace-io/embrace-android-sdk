package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior.Companion.DEFAULT_MAX_SPAN_EVENTS_PER_SESSION_PART
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * Provides the behavior for OpenTelemetry configuration
 */
class OtelBehaviorImpl(
    private val thresholdCheck: BehaviorThresholdCheck,
    local: InstrumentedConfig,
    remote: RemoteConfig?,
) : OtelBehavior {

    private val local = local.enabledFeatures
    private val otelKotlinRemote = remote?.otelKotlinSdkConfig
    private val dataRemote = remote?.dataConfig

    override fun shouldUseKotlinSdk(): Boolean {
        return thresholdCheck.isBehaviorEnabled(otelKotlinRemote?.pctEnabled) ?: local.isOtelKotlinSdkEnabled()
    }

    override fun getMaxSpanEventsPerSessionPart(): Int =
        dataRemote?.maxSpanEventsPerSessionPart ?: DEFAULT_MAX_SPAN_EVENTS_PER_SESSION_PART
}
