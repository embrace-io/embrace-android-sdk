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

    override fun getMaxCustomSpansPerSessionPart(): Int =
        dataRemote?.maxCustomSpansPerSession.asSpanLimit(DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART)

    override fun getMaxInternalSpansPerSessionPart(): Int =
        dataRemote?.maxInternalSpansPerSession.asSpanLimit(DEFAULT_MAX_INTERNAL_SPANS_PER_SESSION_PART)

    override fun getMaxNetworkSpansPerSessionPart(): Int =
        dataRemote?.maxNetworkSpansPerSession.asSpanLimit(DEFAULT_MAX_NETWORK_SPANS_PER_SESSION_PART)

    override fun getMaxSpanEventsPerSessionPart(): Int =
        dataRemote?.maxSpanEventsPerSessionPart ?: DEFAULT_MAX_SPAN_EVENTS_PER_SESSION_PART

    override fun getPeriodicCacheIntervalMs(): Long =
        dataRemote?.periodicCacheIntervalMs?.coerceIn(MIN_PERIODIC_CACHE_INTERVAL_MS, MAX_PERIODIC_CACHE_INTERVAL_MS)
            ?: DEFAULT_PERIODIC_CACHE_INTERVAL_MS

    private fun Int?.asSpanLimit(default: Int): Int = this?.coerceAtLeast(0) ?: default
}
