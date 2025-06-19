package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.otelSpanCreator
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaClock
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanBuilder
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracer
import io.embrace.opentelemetry.kotlin.k2j.ClockAdapter

class EmbTracer(
    private val sdkTracer: OtelJavaTracer,
    private val spanService: SpanService,
    private val clock: OtelJavaClock,
) : OtelJavaTracer {

    override fun spanBuilder(spanName: String): OtelJavaSpanBuilder =
        EmbSpanBuilder(
            otelSpanCreator = sdkTracer.otelSpanCreator(
                name = spanName,
                type = EmbType.Performance.Default,
                private = false,
                internal = false,
                parent = OtelJavaContext.current().getEmbraceSpan(),
                clock = ClockAdapter(clock),
            ),
            spanService = spanService,
            clock = clock,
        )
}
