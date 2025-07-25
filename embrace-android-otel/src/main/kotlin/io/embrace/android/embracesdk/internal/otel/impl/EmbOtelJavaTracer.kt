package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.createContext
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanBuilder
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracer
import io.embrace.opentelemetry.kotlin.tracing.Tracer

@OptIn(ExperimentalApi::class)
class EmbOtelJavaTracer(
    private val sdkTracer: Tracer,
    private val spanService: SpanService,
    private val clock: Clock,
) : OtelJavaTracer {

    override fun spanBuilder(spanName: String): OtelJavaSpanBuilder =
        EmbOtelJavaSpanBuilder(
            otelSpanStartArgs = OtelSpanStartArgs(
                name = spanName,
                type = EmbType.Performance.Default,
                internal = false,
                private = false,
                tracer = sdkTracer,
                parentCtx = OtelJavaContext.current().getEmbraceSpan()?.createContext(),
            ),
            spanService = spanService,
            clock = clock,
        )
}
