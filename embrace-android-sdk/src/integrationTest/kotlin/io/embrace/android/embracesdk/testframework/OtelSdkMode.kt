package io.embrace.android.embracesdk.testframework

import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * The opentelemetry-kotlin implementation an integration test runs against.
 */
internal enum class OtelSdkMode(val useKotlinSdk: Boolean) {

    /**
     * opentelemetry-kotlin's 'compat' implementation, which wraps opentelemetry-java.
     */
    COMPAT(false),

    /**
     * opentelemetry-kotlin's 'regular' implementation, written in pure Kotlin.
     */
    REGULAR(true);

    /**
     * Returns a copy of the config that selects this implementation.
     */
    fun applyTo(config: RemoteConfig): RemoteConfig {
        check(config.otelKotlinSdkConfig == null) {
            "RemoteConfig must not set otelKotlinSdkConfig because SdkIntegrationTestRule sets it from OtelSdkMode."
        }
        return config.copy(
            otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = if (useKotlinSdk) 100.0f else 0.0f),
        )
    }

    /**
     * Returns a copy of the config that selects this implementation locally.
     */
    fun applyTo(config: FakeInstrumentedConfig): FakeInstrumentedConfig = with(config.enabledFeatures) {
        config.copy(
            enabledFeatures = FakeEnabledFeatureConfig(
                base = this,
                hucLiteInstrumentation = isHucLiteInstrumentationEnabled(),
                otelKotlinSdkEnabled = useKotlinSdk,
            ),
        )
    }

    companion object {
        fun parameters(): List<Array<Any>> = entries.map { arrayOf(it) }
    }
}
