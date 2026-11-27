package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.config.OTelConfig
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor

/**
 * Applies [OTelConfig] calls to [target] until closed, after which they are dropped. Each call is guarded separately, so
 * one that fails doesn't stop the others from applying.
 */
internal class OTelConfigImpl(
    private val target: ExternalOtelConfig,
    private val errorHandler: ErrorHandler,
) : OTelConfig, AutoCloseable {

    @Volatile
    private var closed = false

    override fun addSpanExporter(spanExporter: SpanExporter) =
        configure("add_span_exporter") { target.addSpanExporter(spanExporter) }

    override fun addSpanProcessor(spanProcessor: SpanProcessor) =
        configure("add_span_processor") { target.addSpanProcessor(spanProcessor) }

    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) =
        configure("add_log_record_exporter") { target.addLogExporter(logRecordExporter) }

    override fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) =
        configure("add_log_record_processor") { target.addLogRecordProcessor(logRecordProcessor) }

    override fun setResourceAttribute(key: String, value: String) =
        configure("set_resource_attribute") { target.setResourceAttribute(key, value) }

    override fun close() {
        closed = true
    }

    private inline fun configure(action: String, block: () -> Unit) {
        if (closed) {
            errorHandler.log("SDK config is only applied during Embrace.start(), cannot $action.")
            return
        }
        errorHandler.guardSdkCode(SdkErrorType.PublicApiFail, block)
    }
}
