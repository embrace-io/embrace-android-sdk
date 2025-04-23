package io.embrace.android.embracesdk.internal.config.instrumented.schema

import androidx.annotation.Keep

/**
 * Defines the locally set configuration for the SDK. This is typically set from embrace-config.json
 * and instrumented by the embrace gradle plugin, but can be overridden for test purposes.
 */
@Keep
interface InstrumentedConfig {
    val baseUrls: BaseUrlConfig
    val enabledFeatures: EnabledFeatureConfig
    val networkCapture: NetworkCaptureConfig
    val otelLimits: OtelLimitsConfig
    val project: ProjectConfig
    val redaction: RedactionConfig
    val session: SessionConfig
    val symbols: Base64SharedObjectFilesMap
}
