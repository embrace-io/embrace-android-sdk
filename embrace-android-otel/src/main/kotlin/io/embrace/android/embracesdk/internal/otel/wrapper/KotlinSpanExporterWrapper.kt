package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanExporter
import io.embrace.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData

@OptIn(ExperimentalApi::class)
class KotlinSpanExporterWrapper(
    private val impl: SpanExporter
) : OtelJavaSpanExporter { // TODO: tests

    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        val code = impl.export(spans.map(SpanData::toReadableSpan))
        return code.toCompletableResultCode()
    }

    override fun flush(): CompletableResultCode = impl.forceFlush().toCompletableResultCode()

    override fun shutdown(): CompletableResultCode = impl.shutdown().toCompletableResultCode()
}
