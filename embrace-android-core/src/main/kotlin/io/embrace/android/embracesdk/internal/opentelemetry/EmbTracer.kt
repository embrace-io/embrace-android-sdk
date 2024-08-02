package io.embrace.android.embracesdk.internal.opentelemetry

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.getEmbraceSpan
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.Clock

public class EmbTracer(
    private val sdkTracer: Tracer,
    private val spanService: SpanService,
    private val clock: Clock
) : Tracer {

    override fun spanBuilder(spanName: String): SpanBuilder =
        EmbSpanBuilder(
            embraceSpanBuilder = sdkTracer.embraceSpanBuilder(
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
