package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.tracing.Span
import io.embrace.opentelemetry.kotlin.tracing.SpanContext
import io.embrace.opentelemetry.kotlin.tracing.SpanKind
import io.embrace.opentelemetry.kotlin.tracing.SpanRelationships

@ExperimentalApi
class FakeKotlinTracer : io.embrace.opentelemetry.kotlin.tracing.Tracer {

    override fun createSpan(
        name: String,
        parent: SpanContext?,
        spanKind: SpanKind,
        startTimestamp: Long?,
        context: Context?,
        action: SpanRelationships.() -> Unit,
    ): Span = FakeKotlinSpan(
        name,
        parent,
        spanKind,
        startTimestamp,
        context,
    )
}
