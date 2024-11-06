package io.embrace.android.exampleapp

import android.util.Log
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

class LogcatSpanExporter : SpanExporter {

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        for (span in spans) {
            Log.i(
                "EmbraceTestApp",
                "Span: ${span.name}, TraceId: ${span.traceId}, SpanId: ${span.spanId}, Attributes: ${span.attributes}"
            )
        }
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()
    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}
