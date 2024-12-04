package io.embrace.android.embracesdk.internal.config.instrumented.schema

/**
 * Defines the locally set configuration for the SDK. This is typically set from embrace-config.json
 * and instrumented by swazzler, but can be overridden for test purposes.
 */
interface InstrumentedConfig {
    val baseUrls: BaseUrlConfig
    val enabledFeatures: EnabledFeatureConfig
    val networkCapture: NetworkCaptureConfig
    val otelLimits: OtelLimitsConfig
    val project: ProjectConfig
    val redaction: RedactionConfig
    val session: SessionConfig
}
