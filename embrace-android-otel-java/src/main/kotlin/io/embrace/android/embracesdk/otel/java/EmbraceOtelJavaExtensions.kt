package io.embrace.android.embracesdk.otel.java

import io.embrace.android.embracesdk.Embrace
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributeKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordExporter
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaOpenTelemetry
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanExporter
import io.embrace.opentelemetry.kotlin.logging.export.toOtelKotlinLogRecordExporter
import io.embrace.opentelemetry.kotlin.toOtelJavaApi
import io.embrace.opentelemetry.kotlin.tracing.export.toOtelKotlinSpanExporter

/**
 * Adds an [OtelJavaSpanExporter] that OTel Spans will be exported to after completion.
 */
@OptIn(ExperimentalApi::class)
fun Embrace.addJavaSpanExporter(spanExporter: OtelJavaSpanExporter) {
    this.addSpanExporter(spanExporter.toOtelKotlinSpanExporter())
}

/**
 * Adds an [OtelJavaLogRecordExporter] that OTel LogRecords will be exported to after completion.
 */
@OptIn(ExperimentalApi::class)
fun Embrace.addJavaLogRecordExporter(logRecordExporter: OtelJavaLogRecordExporter) {
    this.addLogRecordExporter(logRecordExporter.toOtelKotlinLogRecordExporter())
}

/**
 * Returns an [OtelJavaOpenTelemetry] that provides working [Tracer] implementations that will record spans that fit into the Embrace data
 * model.
 *
 * Note: `sdk_config.otel.enable_otel_kotlin_sdk` must be set to `false` in your embrace-config.json file when using this method.
 * If it is set to `true`, the OtelJavaOpenTelemetry instance may behave inconsistently.
 */
@OptIn(ExperimentalApi::class)
fun Embrace.getJavaOpenTelemetry(): OtelJavaOpenTelemetry {
    return this.getOpenTelemetryKotlin().toOtelJavaApi()
}

/**
 * Set an attribute on the resource used by the OTel SDK instance with the given key and String value.
 * The value set will override any value set previously or by the Embrace SDK.
 * This must be called before the SDK is started in order for it to take effect.
 */
@Deprecated(
    "Use setResourceAttribute(key: String, value: String) instead.",
    ReplaceWith("setResourceAttribute(key.key, value)")
)
fun Embrace.setJavaResourceAttribute(
    key: OtelJavaAttributeKey<String>,
    value: String,
): Unit = setResourceAttribute(key.key, value)
