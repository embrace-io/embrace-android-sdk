package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.BuildConfig
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanExporter
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanProcessor
import io.embrace.android.embracesdk.internal.spans.SpansSink
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter

internal class OpenTelemetryConfiguration(
    spansSink: SpansSink
) {
    val serviceName = BuildConfig.LIBRARY_PACKAGE_NAME
    val serviceVersion = BuildConfig.VERSION_NAME
    private var exporters: MutableList<SpanExporter>

    init {
        exporters = mutableListOf(EmbraceSpanExporter(spansSink))
    }

    val spanProcessor: SpanProcessor by lazy {
        EmbraceSpanProcessor(SpanExporter.composite(exporters))
    }

    fun addSpanExporter(spanExporter: SpanExporter) {
        exporters.add(spanExporter)
    }
}
