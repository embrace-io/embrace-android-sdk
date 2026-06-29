package io.embrace.android.exampleapp

import android.util.Log
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlin.collections.filter

class LogcatSpanExporter : SpanExporter {

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        for (span in spans) {
            Log.i("EmbraceTestApp","[Span] ${span.getSpanSummary()}")
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()
    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()

    private fun SpanData.getSpanSummary(): String {
        val attrs = attributes.asMap().mapKeys { it.key.key }
        val type = attrs["emb.type"]
        val customMessage = when (type) {
            "ux.session" -> {
                attrs.filter { it.key.contains("session") }.let {
                    val id = attrs.entries.single { it.key == "emb.session_part_id" }.value
                    "`Session Part $id` $attrs"
                }
            }
            else -> "`$name`, TraceId: $traceId, SpanId: $spanId, Attributes: $attrs, Events: $events, Links: $links"
        }

        return "$type: $customMessage"
    }
}
