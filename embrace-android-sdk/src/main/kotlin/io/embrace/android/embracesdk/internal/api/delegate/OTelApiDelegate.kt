package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.OTelApi
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor

internal class OTelApiDelegate(
    private val otelSdkWrapper: Lazy<OtelSdkWrapper>,
) : OTelApi {

    override fun addSpanExporter(spanExporter: SpanExporter) {
    }

    override fun addSpanProcessor(spanProcessor: SpanProcessor) {
    }

    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) {
    }

    override fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) {
    }

    override fun setResourceAttribute(key: String, value: String) {
    }

    override fun getOpenTelemetryKotlin(): OpenTelemetry =
        otelSdkWrapper.value.openTelemetryKotlin
}
