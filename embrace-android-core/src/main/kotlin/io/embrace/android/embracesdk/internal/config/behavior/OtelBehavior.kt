package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.EnabledFeatureConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig

/**
 * Provides the behavior for OpenTelemetry configuration
 */
interface OtelBehavior : ConfigBehavior<EnabledFeatureConfig, OtelKotlinSdkConfig> {

    /**
     * Whether the Kotlin OpenTelemetry SDK should be used instead of the Java one.
     * Returns true if the Kotlin SDK should be used, false if it was disabled via remote config.
     */
    fun shouldUseKotlinSdk(): Boolean
}
