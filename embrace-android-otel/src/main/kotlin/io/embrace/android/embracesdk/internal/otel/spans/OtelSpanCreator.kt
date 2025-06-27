package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.toOtelKotlin
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.k2j.tracing.SpanContextAdapter
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import io.embrace.opentelemetry.kotlin.tracing.model.Span
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind

@OptIn(ExperimentalApi::class)
class OtelSpanCreator(
    val spanStartArgs: OtelSpanStartArgs,
    private val tracer: Tracer,
) {
    internal fun startSpan(startTimeMs: Long): Span {
        val parentSpanContext = spanStartArgs.getParentSpanContext()
        return tracer.createSpan(
            name = spanStartArgs.spanName,
            parent = parentSpanContext?.let(::SpanContextAdapter),
            spanKind = spanStartArgs.spanKind?.toOtelKotlin() ?: SpanKind.INTERNAL,
            startTimestamp = startTimeMs.millisToNanos()
        )
    }
}
