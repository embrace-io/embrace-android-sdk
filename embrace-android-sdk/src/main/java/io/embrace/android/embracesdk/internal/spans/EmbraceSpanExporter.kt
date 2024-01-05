package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.annotation.InternalApi
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * Exports the given completed [Span] to the given [SpansService]
 *
 * Note: no explicit tests exist for this as its functionality is tested via the tests for [SpansServiceImpl]
 */
@InternalApi
internal class EmbraceSpanExporter(private val spansService: SpansService) : SpanExporter {
    @Synchronized
    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode =
        spansService.storeCompletedSpans(spans.toList())

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    @Synchronized
    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}
