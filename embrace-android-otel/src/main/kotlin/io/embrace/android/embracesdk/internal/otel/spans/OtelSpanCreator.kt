package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.toOtelKotlin
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.Span
import io.embrace.opentelemetry.kotlin.tracing.SpanKind
import io.embrace.opentelemetry.kotlin.tracing.Tracer

@OptIn(ExperimentalApi::class)
class OtelSpanCreator(
    val spanStartArgs: OtelSpanStartArgs,
    private val tracer: Tracer,
) {

    internal fun startSpan(startTimeMs: Long): Span {
        // FIXME: propagate context correctly
//        val parentSpanContext = spanStartArgs.parentContext.getEmbraceSpan()?.spanContext
//        val parent = parentSpanContext?.let(::SpanContextAdapter)

        return tracer.createSpan(
            name = spanStartArgs.spanName,
            parent = null,
            spanKind = spanStartArgs.spanKind?.toOtelKotlin() ?: SpanKind.INTERNAL,
            startTimestamp = startTimeMs.millisToNanos()
        )
//        val builder = tracer.spanBuilder(spanName)
//        if (parentContext == OtelJavaContext.root()) {
//            builder.setNoParent()
//        } else {
//            builder.setParent(parentContext)
//        }
    }
}
