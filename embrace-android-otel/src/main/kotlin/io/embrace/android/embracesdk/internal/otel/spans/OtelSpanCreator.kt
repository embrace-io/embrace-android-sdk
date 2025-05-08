package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.wrapper.KotlinContextWrapper
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.k2j.tracing.SpanContextAdapter
import io.embrace.opentelemetry.kotlin.tracing.Span
import io.embrace.opentelemetry.kotlin.tracing.SpanKind
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalApi::class)
class OtelSpanCreator(
    val spanStartArgs: OtelSpanStartArgs,
    private val tracer: Tracer,
) {

    internal fun startSpan(startTimeMs: Long): Span {
        with(spanStartArgs) {
            return tracer.createSpan(
                name = spanName,
                parent = spanStartArgs.parentContext.getEmbraceSpan()?.spanContext?.let(::SpanContextAdapter),
                spanKind = spanKind ?: SpanKind.INTERNAL,
                startTimestamp = TimeUnit.MILLISECONDS.toNanos(startTimeMs),
                context = KotlinContextWrapper(spanStartArgs.parentContext),
            )
        }
    }
}
