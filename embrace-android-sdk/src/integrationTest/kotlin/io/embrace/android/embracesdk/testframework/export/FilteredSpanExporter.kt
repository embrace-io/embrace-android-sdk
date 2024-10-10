package io.embrace.android.embracesdk.testframework.export

import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * A [SpanExporter] used in the integration tests that allows retrieving exported spans
 * to perform assertions against.
 */
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

    private fun awaitSpanExport(
        spanFilter: (SpanData) -> Boolean,
        expectedCount: Int,
    ): List<SpanData> {
        val supplier = { spanData.filter(spanFilter) }
        return returnIfConditionMet(
            desiredValueSupplier = supplier,
            dataProvider = supplier,
            condition = { data ->
                data.size == expectedCount
            },
            errorMessageSupplier = {
                "Timeout. Expected $expectedCount spans, but got ${supplier().size}."
            }
        )
    }
}
