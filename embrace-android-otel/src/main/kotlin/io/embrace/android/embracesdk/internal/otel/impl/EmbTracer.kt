package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.getDefaultContext
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import io.embrace.opentelemetry.kotlin.tracing.model.Span
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind
import io.embrace.opentelemetry.kotlin.tracing.model.SpanRelationships

/**
 * Embrace-specific decorator that adds extra logic to OTel Tracing.
 */
@OptIn(ExperimentalApi::class)
class EmbTracer(
    private val impl: Tracer,
    private val openTelemetry: OpenTelemetry,
    private val spanService: SpanService,
    private val clock: Clock,
) : Tracer {

    override fun createSpan(
        name: String,
        parentContext: Context?,
        spanKind: SpanKind,
        startTimestamp: Long?,
        action: SpanRelationships.() -> Unit,
    ): Span {
        val spanCreator = OtelSpanStartArgs(
            name = name,
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            tracer = impl,
            parentCtx = parentContext ?: openTelemetry.getDefaultContext(),
            openTelemetry = openTelemetry,
        )

        spanService.createSpan(spanCreator)?.let { embraceSpan ->
            if (embraceSpan.start()) {
                return EmbSpan(
                    impl = embraceSpan,
                    clock = clock,
                    openTelemetry = openTelemetry,
                )
            }
        }
        return EmbInvalidSpan(openTelemetry)
    }
}
