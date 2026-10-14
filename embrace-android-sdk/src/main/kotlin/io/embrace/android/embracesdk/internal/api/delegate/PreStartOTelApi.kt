package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.OTelApi
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor

internal class PreStartOTelApi(
    private val config: OtelSdkConfig,
    delegate: OTelApi,
) : OTelApi by delegate {

    override fun addSpanExporter(spanExporter: SpanExporter) {
        config.addSpanExporter(spanExporter)
    }

    override fun addSpanProcessor(spanProcessor: SpanProcessor) {
        config.addSpanProcessor(spanProcessor)
    }

    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) {
        config.addLogExporter(logRecordExporter)
    }

    override fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) {
        config.addLogRecordProcessor(logRecordProcessor)
    }

    override fun setResourceAttribute(key: String, value: String) {
        config.setResourceAttribute(key, value)
    }
}
