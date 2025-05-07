package io.embrace.android.embracesdk.internal.otel.spans

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

class OtelSpanCreator(
    val spanStartArgs: OtelSpanStartArgs,
    private val tracer: Tracer,
) {

    internal fun startSpan(startTimeMs: Long): Span {
        with(spanStartArgs) {
            val builder = tracer.spanBuilder(spanName)
            if (parentContext == Context.root()) {
                builder.setNoParent()
            } else {
                builder.setParent(parentContext)
            }

            spanKind?.let(builder::setSpanKind)
            builder.setStartTimestamp(startTimeMs, TimeUnit.MILLISECONDS)
            return builder.startSpan()
        }
    }
}
