package io.embrace.android.embracesdk.testframework.export

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

internal class FilteredSpanExporter : SpanExporter {

    private val spanData = mutableListOf<SpanData>()

    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        spanData.addAll(spans)
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    fun awaitSpansWithType(type: EmbType, expectedCount: Int): List<SpanData> {
        return awaitSpanExport({
            it.attributes.asMap().any { entry ->
                entry.key.key == "emb.type" && entry.value == type.value
            }
        }, expectedCount)
    }

    fun awaitSpanExport( // TODO: future use latch
        spanFilter: (SpanData) -> Boolean,
        expectedCount: Int
    ): List<SpanData> {
        val spans = spanData
        val receivedSize = spans.count(spanFilter)

        if (receivedSize == expectedCount) {
            return spans.filter(spanFilter)
        } else {
            error("Unexpected span count: $receivedSize")
        }
    }
}