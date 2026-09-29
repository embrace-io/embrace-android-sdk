package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanExporter

/**
 * Wraps a [SpanExporter] supplied by the SDK user so that it never sees spans that are private to Embrace.
 */
internal class PrivateTelemetryFilteringSpanExporter(
    private val delegate: SpanExporter,
) : SpanExporter by delegate {

    override suspend fun export(telemetry: List<SpanData>): OperationResultCode =
        delegate.export(telemetry.filterNot { it.attributes.containsKey(PrivateSpan.key) })
}
