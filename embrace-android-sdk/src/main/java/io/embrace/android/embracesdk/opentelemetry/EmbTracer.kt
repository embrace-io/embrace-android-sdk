package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanBuilder
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.common.Clock

internal class EmbTracer(
    private val sdkTracer: Tracer,
    private val spanService: SpanService,
    private val clock: Clock
) : Tracer {

    override fun spanBuilder(spanName: String): SpanBuilder =
        EmbSpanBuilder(
            embraceSpanBuilder = EmbraceSpanBuilder(
                tracer = sdkTracer,
                name = spanName,
                telemetryType = EmbType.Performance.Default,
                private = false,
                internal = false,
                parent = null,
            ),
            spanService = spanService,
            clock = clock,
        )
}
