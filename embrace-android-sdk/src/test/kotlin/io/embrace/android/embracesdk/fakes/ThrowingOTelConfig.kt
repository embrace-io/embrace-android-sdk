package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.config.OTelConfig
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor

internal class ThrowingOTelConfig : OTelConfig {
    override fun addSpanExporter(spanExporter: SpanExporter) = error("addSpanExporter")
    override fun addSpanProcessor(spanProcessor: SpanProcessor) = error("addSpanProcessor")
    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) = error("addLogRecordExporter")
    override fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) = error("addLogRecordProcessor")
    override fun setResourceAttribute(key: String, value: String) = error("setResourceAttribute")
}
