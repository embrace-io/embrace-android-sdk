@file:Suppress("unused")

package io.embrace.android.embracesdk.otel.java

import android.util.Log
import io.embrace.android.embracesdk.config.OTelConfig
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.opentelemetry.api.OpenTelemetry
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
public fun OTelConfig.addJavaSpanExporter(spanExporter: OtelJavaSpanExporter) {
    guardJavaInterop("add a Java span exporter") {
        addSpanExporter(spanExporter.toOtelKotlinSpanExporter())
    }
}

/**
 * Adds an [OtelJavaLogRecordExporter] that OTel LogRecords will be exported to after completion.
 */
public fun OTelConfig.addJavaLogRecordExporter(logRecordExporter: OtelJavaLogRecordExporter) {
    guardJavaInterop("add a Java log record exporter") {
        addLogRecordExporter(logRecordExporter.toOtelKotlinLogRecordExporter())
    }
}

/**
 * Adds a [OtelJavaSpanProcessor] that will process OTel Spans after Embrace's internal processor.
 */
public fun OTelConfig.addJavaSpanProcessor(spanProcessor: OtelJavaSpanProcessor) {
    guardJavaInterop("add a Java span processor") {
        addSpanProcessor(spanProcessor.toOtelKotlinSpanProcessor())
    }
}

/**
 * Adds a [OtelJavaLogRecordProcessor] that will process OTel Logs after Embrace's internal processor.
 */
public fun OTelConfig.addJavaLogRecordProcessor(logRecordProcessor: OtelJavaLogRecordProcessor) {
    guardJavaInterop("add a Java log record processor") {
        addLogRecordProcessor(logRecordProcessor.toOtelKotlinLogRecordProcessor())
    }
}

/**
 * Returns an [OtelJavaOpenTelemetry] that provides working [Tracer][io.opentelemetry.api.trace.Tracer] implementations that will
 * record spans that fit into the Embrace data model. Returns a no-op instance if it can't be created.
 *
 * Note: `sdk_config.otel.enable_otel_kotlin_sdk` must be set to `false` in your embrace-config.json file when using this method.
 * If it is set to `true`, the OtelJavaOpenTelemetry instance may behave inconsistently.
 */
public fun SdkApi.getJavaOpenTelemetry(): OtelJavaOpenTelemetry =
    guardJavaInterop("create a Java OpenTelemetry instance", fallback = { OpenTelemetry.noop() }) {
        getOpenTelemetryKotlin().toOtelJavaApi()
    }

/**
 * Runs [block], logging anything it throws and returning the result of [fallback] instead, so that bridging to the OTel
 * Java API can't crash the app.
 */
private inline fun guardJavaInterop(action: String, block: () -> Unit) = guardJavaInterop(action, {}, block)

private inline fun <T> guardJavaInterop(action: String, fallback: () -> T, block: () -> T): T = try {
    block()
} catch (exc: Throwable) {
    Log.w("Embrace", "Failed to $action", exc)
    fallback()
}
