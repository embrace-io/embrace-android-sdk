package io.embrace.android.embracesdk.otel.java

import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.opentelemetry.kotlin.ExperimentalApi
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
fun SdkApi.addJavaSpanExporter(spanExporter: OtelJavaSpanExporter) {
    this.addSpanExporter(spanExporter.toOtelKotlinSpanExporter())
}

/**
 * Adds an [OtelJavaLogRecordExporter] that OTel LogRecords will be exported to after completion.
 */
@OptIn(ExperimentalApi::class)
fun SdkApi.addJavaLogRecordExporter(logRecordExporter: OtelJavaLogRecordExporter) {
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
fun SdkApi.getJavaOpenTelemetry(): OtelJavaOpenTelemetry {
    return this.getOpenTelemetryKotlin().toOtelJavaApi()
}
