package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracer
import java.util.concurrent.TimeUnit

class OtelSpanCreator(
    val spanStartArgs: OtelSpanStartArgs,
    private val tracer: OtelJavaTracer,
) {

    internal fun startSpan(startTimeMs: Long): OtelJavaSpan {
        with(spanStartArgs) {
            val builder = tracer.spanBuilder(spanName)
            if (parentContext == OtelJavaContext.root()) {
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
