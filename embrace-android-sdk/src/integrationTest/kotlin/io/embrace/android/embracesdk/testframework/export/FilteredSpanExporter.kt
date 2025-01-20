package io.embrace.android.embracesdk.testframework.export

import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [SpanExporter] used in the integration tests that allows retrieving exported spans
 * to perform assertions against.
 */
internal class FilteredSpanExporter : SpanExporter {

    private val spanData = CopyOnWriteArrayList<SpanData>()

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

    fun awaitSpans(expectedCount: Int, filter: (SpanData) -> Boolean): List<SpanData> {
        val supplier = { spanData.filter(filter) }
        return returnIfConditionMet(
            desiredValueSupplier = supplier,
            dataProvider = supplier,
            condition = { data ->
                data.size == expectedCount
            },
            errorMessageSupplier = {
                val spans = supplier()
                "Timeout. Expected $expectedCount spans, but got ${spans.size}. " +
                    "Found spans: ${spans.joinToString { it.name }}"
            }
        )
    }

    fun awaitSpansWithType(expectedCount: Int, type: EmbType): List<SpanData> {
        return awaitSpans(expectedCount) { data ->
            data.attributes?.asMap()?.mapKeys { it.key.key }?.get("emb.type") == type.value
        }
    }

    fun failOnDuplicate() {
        val exportedSpans = spanData.toList()
        val seen = exportedSpans.map { it.spanId }.distinct()
        val duplicates = exportedSpans
            .filterNot {
                seen.contains(it.spanId)
            }.map {
                Pair(
                    "spanId" to it.spanId,
                    "name" to it.name,
                )
            }.distinct()
        if (duplicates.isNotEmpty()) {
            error("Duplicate spans exported: $duplicates")
        }
    }
}
