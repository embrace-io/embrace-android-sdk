package io.embrace.android.embracesdk.config

import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor

/**
 * Configures the OpenTelemetry SDK that Embrace builds when it starts.
 */
@SdkConfigDsl
public interface OTelConfig {

    /**
     * Adds a [SpanExporter] that OTel Spans will be exported to after completion.
     */
    public fun addSpanExporter(spanExporter: SpanExporter)

    /**
     * Adds a [SpanProcessor] that will process OTel Spans after Embrace's internal processor.
     */
    public fun addSpanProcessor(spanProcessor: SpanProcessor)

    /**
     * Adds a [LogRecordExporter] that OTel Logs will be exported to after logging.
     */
    public fun addLogRecordExporter(logRecordExporter: LogRecordExporter)

    /**
     * Adds a [LogRecordProcessor] that will process OTel Logs after Embrace's internal processor.
     */
    public fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor)

    /**
     * Sets an attribute on the resource used by the OTel SDK. The value overrides any value set previously or by the
     * Embrace SDK.
     */
    public fun setResourceAttribute(key: String, value: String)
}
