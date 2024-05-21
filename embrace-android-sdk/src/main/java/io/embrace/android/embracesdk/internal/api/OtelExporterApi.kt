package io.embrace.android.embracesdk.internal.api

import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * Internal API used for exporting OTel data.
 */
internal interface OtelExporterApi {
    fun addLogRecordExporter(logRecordExporter: LogRecordExporter)
    fun addSpanExporter(spanExporter: SpanExporter)
}
