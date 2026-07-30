package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig

/**
 * Provides the behavior for OpenTelemetry configuration.
 *
 * This is derived purely from local (instrumented) config. The choice of OTel SDK is made very
 * early in startup - before the span service is initialised and long before remote config can be
 * fetched from the network - so it cannot be driven by remote config.
 */
class OtelBehaviorImpl(
    local: InstrumentedConfig,
) : OtelBehavior {

    private val local = local.enabledFeatures

    override fun shouldUseKotlinSdk(): Boolean = local.isOtelKotlinSdkEnabled()
}
