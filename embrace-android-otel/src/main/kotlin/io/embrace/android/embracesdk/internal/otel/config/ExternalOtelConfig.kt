package io.embrace.android.embracesdk.internal.otel.config

import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * OpenTelemetry configuration supplied by the app before the SDK starts.
 *
 * The app can still add to it on another thread while the SDK starts and reads it, so each list is copy-on-write: a read
 * iterates a snapshot rather than failing on a concurrent add.
 */
class ExternalOtelConfig {

    private val spanExporters = CopyOnWriteArrayList<SpanExporter>()
    private val spanProcessors = CopyOnWriteArrayList<SpanProcessor>()
    private val logExporters = CopyOnWriteArrayList<LogRecordExporter>()
    private val logRecordProcessors = CopyOnWriteArrayList<LogRecordProcessor>()
    private val resourceAttributes: MutableMap<String, String> = ConcurrentHashMap()

    fun addSpanExporter(spanExporter: SpanExporter) {
        spanExporters.add(spanExporter)
    }

    fun addSpanProcessor(spanProcessor: SpanProcessor) {
        spanProcessors.add(spanProcessor)
    }

    fun addLogExporter(logExporter: LogRecordExporter) {
        logExporters.add(logExporter)
    }

    fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) {
        logRecordProcessors.add(logRecordProcessor)
    }

    fun setResourceAttribute(key: String, value: String) {
        resourceAttributes[key] = value
    }

    fun getSpanExporters(): List<SpanExporter> = spanExporters

    fun getSpanProcessors(): List<SpanProcessor> = spanProcessors

    fun getLogExporters(): List<LogRecordExporter> = logExporters

    fun getLogRecordProcessors(): List<LogRecordProcessor> = logRecordProcessors

    fun getResourceAttributes(): Map<String, String> = resourceAttributes

    fun hasConfiguredOtlpExport(): Boolean = logExporters.isNotEmpty() || logRecordProcessors.isNotEmpty() ||
        spanExporters.isNotEmpty() || spanProcessors.isNotEmpty()
}
