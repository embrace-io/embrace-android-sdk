package io.embrace.android.embracesdk.internal.api

import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * Internal API used for exporting OTel data.
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
     * Returns a [Tracer] that can be used to log spans. This instance will identify itself as the Embrace SDK.
     */
    fun getTracer(): Tracer = getTracer(null, null)

    /**
     * Returns a [Tracer] that can be used to log spans. This instance will identify itself with the given [instrumentationModuleName] if
     * it's non-null.
     */
    fun getTracer(instrumentationModuleName: String?): Tracer = getTracer(instrumentationModuleName, null)

    /**
     * Returns a [Tracer] that can be used to log spans. This instance will identify itself with the given [instrumentationModuleName]
     * and [instrumentationModuleVersion] if the former is non-null.
     */
    fun getTracer(instrumentationModuleName: String?, instrumentationModuleVersion: String?): Tracer
}
