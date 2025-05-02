package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.internal.EmbTrace
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * Exports the given completed [Span] to the given [SpanSink] as well as any configured external [SpanExporter]
 */
internal class EmbraceSpanExporter(
    private val spanSink: SpanSink,
    private val externalSpanExporter: SpanExporter?,
    private val exportCheck: () -> Boolean,
) : SpanExporter {
    @Synchronized
    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        if (!exportCheck()) {
            return CompletableResultCode.ofSuccess()
        }
        val result = spanSink.storeCompletedSpans(spans.toList())
        if (externalSpanExporter != null && result == CompletableResultCode.ofSuccess()) {
            return EmbTrace.trace("otel-external-export") {
                externalSpanExporter.export(spans.filterNot { it.hasFixedAttribute(PrivateSpan) })
            }
        }

        return result
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    @Synchronized
    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}
