package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.otelSpanCreator
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import io.embrace.opentelemetry.kotlin.tracing.model.Span
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind
import io.embrace.opentelemetry.kotlin.tracing.model.SpanRelationships

/**
 * Embrace-specific decorator that adds extra logic to OTel Tracing.
 */
@OptIn(ExperimentalApi::class)
class EmbTracer(
    private val impl: Tracer,
    private val spanService: SpanService,
    private val clock: Clock,
) : Tracer {

    override fun createSpan(
        name: String,
        parent: SpanContext?,
        spanKind: SpanKind,
        startTimestamp: Long?,
        action: SpanRelationships.() -> Unit,
    ): Span {
        val spanCreator = impl.otelSpanCreator(
            name = name,
            type = EmbType.Performance.Default,
            private = false,
            internal = false,
            parent = OtelJavaContext.current().getEmbraceSpan(),
        )

        spanService.createSpan(spanCreator)?.let { embraceSpan ->
            if (embraceSpan.start()) {
                return EmbSpan(
                    impl = embraceSpan,
                    clock = clock,
                )
            }
        }
        return EmbInvalidSpan()
    }
}
