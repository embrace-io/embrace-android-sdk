package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.embraceSpanBuilder
import io.embrace.android.embracesdk.spans.getEmbraceSpan
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.common.Clock

internal class EmbTracer(
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
