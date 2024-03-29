package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.internal.logs.EmbraceLogRecordExporter
import io.embrace.android.embracesdk.internal.logs.EmbraceLogRecordProcessor
import io.embrace.android.embracesdk.internal.logs.LogSink
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanExporter
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanProcessor
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter

internal class OpenTelemetryConfiguration(
    spanSink: SpanSink,
    logSink: LogSink
) {
    val serviceName = BuildConfig.LIBRARY_PACKAGE_NAME
    val serviceVersion = BuildConfig.VERSION_NAME
    private val spanExporters = mutableListOf<SpanExporter>(EmbraceSpanExporter(spanSink))
    private val logExporters = mutableListOf<LogRecordExporter>(EmbraceLogRecordExporter(logSink))

    val spanProcessor: SpanProcessor by lazy {
        EmbraceSpanProcessor(SpanExporter.composite(spanExporters))
    }

    val logProcessor: LogRecordProcessor by lazy {
        EmbraceLogRecordProcessor(LogRecordExporter.composite(logExporters))
    }

    fun addSpanExporter(spanExporter: SpanExporter) {
        spanExporters.add(spanExporter)
    }

    fun addLogExporter(logExporter: LogRecordExporter) {
        logExporters.add(logExporter)
    }
}
