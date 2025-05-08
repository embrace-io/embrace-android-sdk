package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState

class KotlinSpanContextWrapper(
    private val impl: SpanContext,
) : io.opentelemetry.api.trace.SpanContext {

    override fun getTraceId(): String = impl.traceId
    override fun getSpanId(): String = impl.spanId
    override fun isRemote(): Boolean = impl.isRemote

    override fun getTraceFlags(): TraceFlags = KotlinTraceFlagsWrapper(impl.traceFlags)
    override fun getTraceState(): TraceState = KotlinTraceStateWrapper(impl.traceState)
}
