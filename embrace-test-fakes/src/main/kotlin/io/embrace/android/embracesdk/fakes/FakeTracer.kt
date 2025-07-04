package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.sdk.TracerKey
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import io.embrace.opentelemetry.kotlin.tracing.model.Span
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind
import io.embrace.opentelemetry.kotlin.tracing.model.SpanRelationships

@OptIn(ExperimentalApi::class)
class FakeTracer(
    val tracerKey: TracerKey = TracerKey(instrumentationScopeName = "fake-scope"),
) : Tracer {
    override fun createSpan(
        name: String,
        parent: SpanContext?,
        spanKind: SpanKind,
        startTimestamp: Long?,
        action: SpanRelationships.() -> Unit,
    ): Span = FakeSpan()
}
