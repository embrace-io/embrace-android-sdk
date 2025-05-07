package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.otelSpanCreator
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.Clock

class EmbTracer(
    private val sdkTracer: Tracer,
    private val spanService: SpanService,
    private val clock: Clock,
) : Tracer {

    override fun spanBuilder(spanName: String): SpanBuilder =
        EmbSpanBuilder(
            otelSpanCreator = sdkTracer.otelSpanCreator(
                name = spanName,
                type = EmbType.Performance.Default,
                private = false,
                internal = false,
                parent = Context.current().getEmbraceSpan(),
            ),
            spanService = spanService,
            clock = clock,
        )
}
