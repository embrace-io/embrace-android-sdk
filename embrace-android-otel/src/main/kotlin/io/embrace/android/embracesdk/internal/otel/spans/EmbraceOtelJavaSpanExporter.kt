package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.toCompleteableResultCode
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaCompletableResultCode
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanExporter

/**
 * Exports the given completed [Span] to the given [SpanSink] as well as any configured external [SpanExporter]
 */
internal class EmbraceOtelJavaSpanExporter(
    private val spanSink: SpanSink,
    private val externalSpanExporter: OtelJavaSpanExporter?,
    private val exportCheck: () -> Boolean,
) : OtelJavaSpanExporter {

    @Synchronized
    override fun export(spans: MutableCollection<OtelJavaSpanData>): OtelJavaCompletableResultCode {
        if (!exportCheck()) {
            return StoreDataResult.SUCCESS.toCompleteableResultCode()
        }
        val result = spanSink.storeCompletedSpans(spans.toList())
        if (externalSpanExporter != null && result == StoreDataResult.SUCCESS) {
            return EmbTrace.trace("otel-external-export") {
                externalSpanExporter.export(spans.filterNot { it.attributes.hasEmbraceAttribute(PrivateSpan) })
            }
        }
        return result.toCompleteableResultCode()
    }

    override fun flush(): OtelJavaCompletableResultCode = StoreDataResult.SUCCESS.toCompleteableResultCode()

    @Synchronized
    override fun shutdown(): OtelJavaCompletableResultCode = StoreDataResult.SUCCESS.toCompleteableResultCode()
}
