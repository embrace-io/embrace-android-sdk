@file:Suppress("unused")

package io.embrace.android.embracesdk.otel.java

import io.embrace.android.embracesdk.internal.api.SdkApi
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.aliases.OtelJavaLogRecordExporter
import io.opentelemetry.kotlin.aliases.OtelJavaLogRecordProcessor
import io.opentelemetry.kotlin.aliases.OtelJavaOpenTelemetry
import io.opentelemetry.kotlin.aliases.OtelJavaSpanExporter
import io.opentelemetry.kotlin.aliases.OtelJavaSpanProcessor
import io.opentelemetry.kotlin.logging.export.toOtelKotlinLogRecordExporter
import io.opentelemetry.kotlin.logging.export.toOtelKotlinLogRecordProcessor
import io.opentelemetry.kotlin.toOtelJavaApi
import io.opentelemetry.kotlin.tracing.export.toOtelKotlinSpanExporter
import io.opentelemetry.kotlin.tracing.export.toOtelKotlinSpanProcessor

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
 * Adds a [OtelJavaSpanProcessor] that will process OTel Spans after Embrace's internal processor.
 * Processors must be added before the SDK has started or they will be ignored.
 */
@OptIn(ExperimentalApi::class)
fun SdkApi.addJavaSpanProcessor(spanProcessor: OtelJavaSpanProcessor) {
    this.addSpanProcessor(spanProcessor.toOtelKotlinSpanProcessor())
}

/**
 * Adds a [OtelJavaLogRecordProcessor] that will process OTel Logs after Embrace's internal processor.
 * Processors must be added before the SDK has started or they will be ignored.
 */
@OptIn(ExperimentalApi::class)
fun SdkApi.addJavaLogRecordProcessor(logRecordProcessor: OtelJavaLogRecordProcessor) {
    this.addLogRecordProcessor(logRecordProcessor.toOtelKotlinLogRecordProcessor())
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
