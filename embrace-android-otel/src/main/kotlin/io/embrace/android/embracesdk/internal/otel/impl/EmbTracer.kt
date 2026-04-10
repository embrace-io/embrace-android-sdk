package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.getDefaultContext
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanCreationAction
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.Tracer

/**
 * Embrace-specific decorator that adds extra logic to OTel Tracing.
 */
class EmbTracer(
    private val impl: Tracer,
    private val openTelemetry: OpenTelemetry,
    private val spanService: SpanService,
    private val clock: Clock,
    private val useKotlinSdk: Boolean
) : Tracer {

    override fun startSpan(
        name: String,
        parentContext: Context?,
        spanKind: SpanKind,
        startTimestamp: Long?,
        action: (SpanCreationAction.() -> Unit)?,
    ): Span {
        val spanCreator = OtelSpanStartArgs(
            name = name,
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            tracer = impl,
            parentCtx = parentContext ?: openTelemetry.getDefaultContext(useKotlinSdk),
            openTelemetry = openTelemetry,
            spanKind = spanKind,
            startTimeMs = startTimestamp?.nanosToMillis()
        )
        var span: Span? = null
        spanService.createSpan(spanCreator).let { embraceSpan ->
            if (embraceSpan.start()) {
                span = EmbSpan(
                    impl = embraceSpan,
                    clock = clock,
                    openTelemetry = openTelemetry,
                )
            }
        }
        val ref = span ?: EmbInvalidSpan(openTelemetry)
        if (action != null) {
            action(ref as SpanCreationAction)
        }
        return ref
    }
}
