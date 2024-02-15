package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanExporter
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanProcessor
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter

internal class OpenTelemetryConfiguration(
    spanSink: SpanSink
) {
    val serviceName = BuildConfig.LIBRARY_PACKAGE_NAME
    val serviceVersion = BuildConfig.VERSION_NAME
    private val exporters = mutableListOf<SpanExporter>(EmbraceSpanExporter(spanSink))

    val spanProcessor: SpanProcessor by lazy {
        EmbraceSpanProcessor(SpanExporter.composite(exporters))
    }

    fun addSpanExporter(spanExporter: SpanExporter) {
        exporters.add(spanExporter)
    }
}
