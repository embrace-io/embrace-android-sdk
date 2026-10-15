package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.OTelApi
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor

/**
 * Applies OTel configuration calls made before the SDK starts directly to the [ExternalOtelConfig], before the
 * OTel SDK is built.
 */
internal class PreStartOTelApi(
    private val config: ExternalOtelConfig,
) : OTelApi {

    override fun getOpenTelemetryKotlin(): OpenTelemetry = NoopOpenTelemetry

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
