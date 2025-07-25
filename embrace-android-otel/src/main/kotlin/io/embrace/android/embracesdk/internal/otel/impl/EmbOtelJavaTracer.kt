package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.otelSpanArgs
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
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
            otelSpanStartArgs = sdkTracer.otelSpanArgs(
                name = spanName,
                type = EmbType.Performance.Default,
                private = false,
                internal = false,
                parent = OtelJavaContext.current().getEmbraceSpan(),
            ),
            spanService = spanService,
            clock = clock,
        )
}
