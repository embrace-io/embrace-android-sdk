package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.otel.getDefaultContext
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanCreationAction
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.Tracer

/**
 * Embrace-specific decorator that adds extra logic to OTel Tracing.
 */
class EmbTracer(
    private val impl: Tracer,
    private val openTelemetry: OpenTelemetry,
    private val spanService: SpanService,
    private val clock: Clock,
    private val errorHandler: ErrorHandler,
) : Tracer {

    override fun enabled(): Boolean = impl.enabled()

    override fun startSpan(
        name: String,
        parentContext: Context?,
        spanKind: SpanKind,
        startTimestamp: Long?,
        action: (SpanCreationAction.() -> Unit)?,
    ): Span {
        val span = errorHandler.guardSdkCode(SdkErrorType.SpanOperationFail, null) {
            val spanCreator = OtelSpanStartArgs(
                name = name,
                type = EmbType.Performance.Default,
                internal = false,
                private = false,
                tracer = impl,
                parentCtx = parentContext ?: openTelemetry.getDefaultContext(),
                openTelemetry = openTelemetry,
                spanKind = spanKind,
                startTimeMs = startTimestamp?.nanosToMillis(),
            )
            val embraceSpan = spanService.createSpan(spanCreator)
            if (embraceSpan.start()) {
                EmbSpan(
                    impl = embraceSpan,
                    clock = clock,
                    errorHandler = errorHandler,
                )
            } else {
                null
            }
        }
        val ref = span ?: EmbInvalidSpan()
        if (action != null) {
            try {
                action(ref as SpanCreationAction)
            } catch (exc: Throwable) {
                // the span is never returned to the caller, so end it here or it would leak
                ref.setStatus(StatusData.Error(null))
                ref.end()
                throw exc
            }
        }
        return ref
    }
}
