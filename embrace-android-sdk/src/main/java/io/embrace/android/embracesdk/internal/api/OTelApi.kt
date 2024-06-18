package io.embrace.android.embracesdk.internal.api

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * Methods that enable integration with the the large OTel ecosystem through standard OTel APIs and concepts.
 */
internal interface OTelApi {

    /**
     * Add a [LogRecordExporter] that OTel Logs will be exported to after logging
     */
    fun addLogRecordExporter(logRecordExporter: LogRecordExporter)

    /**
     * Adds a [SpanExporter] that OTel Spans will be exported to after completion
     */
    fun addSpanExporter(spanExporter: SpanExporter)

    /**
     * Returns an [OpenTelemetry] that provides working [Tracer] implementations that will record spans that fit into the Embrace data
     * model.
     */
    fun getOpenTelemetry(): OpenTelemetry
}
