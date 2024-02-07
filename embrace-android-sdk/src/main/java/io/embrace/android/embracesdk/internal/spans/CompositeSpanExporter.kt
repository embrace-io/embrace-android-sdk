package io.embrace.android.embracesdk.internal.spans

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

public class CompositeSpanExporter : SpanExporter {

    private val exporters: MutableList<SpanExporter> = mutableListOf()

    public fun add(spanExporter: SpanExporter) {
        this.exporters.add(spanExporter)
    }

    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        return CompletableResultCode.ofAll(exporters.map { it.export(spans) })
    }

    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofAll(exporters.map { it.flush() })
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofAll(exporters.map { it.shutdown() })
    }
}
