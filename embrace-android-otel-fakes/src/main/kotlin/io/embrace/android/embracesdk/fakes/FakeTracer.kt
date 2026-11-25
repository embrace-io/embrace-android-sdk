package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.SpanCreationAction

class FakeTracer : Tracer {

    val actions = mutableListOf<(SpanCreationAction.() -> Unit)?>()

    override fun enabled(): Boolean = true

    override fun startSpan(
        name: String,
        parentContext: Context?,
        spanKind: SpanKind,
        startTimestamp: Long?,
        action: (SpanCreationAction.() -> Unit)?,
    ): Span {
        actions.add(action)
        return FakeSpan()
    }
}
